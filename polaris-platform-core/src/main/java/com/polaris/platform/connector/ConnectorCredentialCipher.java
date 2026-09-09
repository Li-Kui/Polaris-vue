package com.polaris.platform.connector;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.polaris.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 外部连接凭证加解密组件。
 *
 * <p>密文信封携带版本前缀，便于后续升级算法；无法识别为信封的数据按历史明文兼容读取。</p>
 */
@Component
public class ConnectorCredentialCipher {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public ConnectorCredentialCipher(
            @Value("${platform.connector.credential-key:${platform.jwt.secret:}}")
            String keyMaterial) {
        this.secretKey = buildKey(keyMaterial);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        ensureConfigured();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            String ciphertext = PREFIX + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
            // auth_config 是 JSON 列，使用加密信封保证密文可直接持久化。
            JSONObject envelope = new JSONObject();
            envelope.put("version", 1);
            envelope.put("ciphertext", ciphertext);
            return envelope.toJSONString();
        } catch (Exception e) {
            throw new ServiceException("连接凭证加密失败");
        }
    }

    public String decrypt(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        String ciphertext = encryptedValue(storedValue);
        // 兼容升级前保存的明文 JSON；数据再次保存时会自动加密。
        if (ciphertext == null) {
            return storedValue;
        }
        ensureConfigured();
        try {
            String[] parts = ciphertext.substring(PREFIX.length()).split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("密文格式错误");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ServiceException("连接凭证解密失败，请检查凭证主密钥");
        }
    }

    public boolean isEncrypted(String value) {
        return encryptedValue(value) != null;
    }

    private String encryptedValue(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        // 兼容开发阶段可能产生的裸密文格式。
        if (storedValue.startsWith(PREFIX)) {
            return storedValue;
        }
        try {
            JSONObject envelope = JSON.parseObject(storedValue);
            String ciphertext = envelope.getString("ciphertext");
            return ciphertext != null && ciphertext.startsWith(PREFIX) ? ciphertext : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private SecretKeySpec buildKey(String keyMaterial) {
        if (keyMaterial == null || keyMaterial.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("初始化连接凭证主密钥失败", e);
        }
    }

    private void ensureConfigured() {
        if (secretKey == null) {
            throw new ServiceException("未配置连接凭证主密钥");
        }
    }
}
