package com.polaris.ai.workflow.runtime;

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

/** 单节点试运行可恢复载荷的短期加密组件。 */
@Component
public class WorkflowNodeTestPayloadCipher {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public WorkflowNodeTestPayloadCipher(
            @Value("${ai.workflow.node-test-payload-key:${platform.connector.credential-key:${platform.jwt.secret:}}}")
            String keyMaterial) {
        this.secretKey = buildKey(keyMaterial);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        ensureConfigured();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new ServiceException("试运行恢复载荷加密失败");
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        if (!ciphertext.startsWith(PREFIX)) {
            throw new ServiceException("试运行恢复载荷格式无效");
        }
        ensureConfigured();
        try {
            String[] parts = ciphertext.substring(PREFIX.length()).split(":", 2);
            if (parts.length != 2) throw new IllegalArgumentException("密文格式错误");
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ServiceException("试运行恢复载荷解密失败，请检查载荷主密钥");
        }
    }

    private SecretKeySpec buildKey(String keyMaterial) {
        if (keyMaterial == null || keyMaterial.isBlank()) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("初始化试运行恢复载荷主密钥失败", e);
        }
    }

    private void ensureConfigured() {
        if (secretKey == null) {
            throw new ServiceException("未配置试运行恢复载荷主密钥");
        }
    }
}
