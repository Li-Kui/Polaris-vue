package com.polaris.ai.modelcenter.credential;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.polaris.ai.modelcenter.config.ModelCenterCredentialProperties;
import com.polaris.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** 使用带身份 AAD 的 AES-256-GCM 加密 Provider Credential。 */
@Service
public class AesGcmProviderCredentialService implements ProviderCredentialService {

    private static final int ENVELOPE_VERSION = 1;
    private static final String ALGORITHM = "AES-256-GCM";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BYTES = 16;
    private static final int TAG_LENGTH_BITS = TAG_LENGTH_BYTES * 8;

    private final ModelCenterCredentialProperties properties;
    private final SecureRandom secureRandom;
    private final String platformCredentialKey;

    @Autowired
    public AesGcmProviderCredentialService(
            ModelCenterCredentialProperties properties,
            @Value("${platform.connector.credential-key:${platform.jwt.secret:}}")
            String platformCredentialKey) {
        this(properties, new SecureRandom(), platformCredentialKey);
    }

    AesGcmProviderCredentialService(ModelCenterCredentialProperties properties) {
        this(properties, new SecureRandom(), null);
    }

    AesGcmProviderCredentialService(
            ModelCenterCredentialProperties properties,
            SecureRandom secureRandom) {
        this(properties, secureRandom, null);
    }

    private AesGcmProviderCredentialService(
            ModelCenterCredentialProperties properties,
            SecureRandom secureRandom,
            String platformCredentialKey) {
        this.properties = properties;
        this.secureRandom = secureRandom;
        this.platformCredentialKey = platformCredentialKey;
    }

    @Override
    public String encrypt(
            Map<String, Object> credential,
            ProviderCredentialContext context) {
        if (credential == null || credential.isEmpty()) {
            return null;
        }
        String keyId = requireText(properties.getActiveKeyId(), "未配置凭据主密钥标识");
        SecretKeySpec key = decodeKey(activeKeyMaterial());
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(context.aad());
            byte[] encryptedWithTag = cipher.doFinal(
                    JSON.toJSONString(credential).getBytes(StandardCharsets.UTF_8));
            int tagOffset = encryptedWithTag.length - TAG_LENGTH_BYTES;

            JSONObject envelope = new JSONObject();
            envelope.put("v", ENVELOPE_VERSION);
            envelope.put("alg", ALGORITHM);
            envelope.put("kid", keyId);
            envelope.put("iv", encode(iv));
            envelope.put("ciphertext", encode(Arrays.copyOf(encryptedWithTag, tagOffset)));
            envelope.put("tag", encode(Arrays.copyOfRange(
                    encryptedWithTag, tagOffset, encryptedWithTag.length)));
            return envelope.toJSONString();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Provider Credential 加密失败");
        }
    }

    @Override
    public Map<String, Object> decrypt(
            String ciphertext,
            ProviderCredentialContext context) {
        if (!isConfigured(ciphertext)) {
            return Map.of();
        }
        try {
            JSONObject envelope = JSON.parseObject(ciphertext);
            validateEnvelope(envelope);
            SecretKeySpec key = keyFor(envelope.getString("kid"));
            byte[] encrypted = decode(envelope.getString("ciphertext"));
            byte[] tag = decode(envelope.getString("tag"));
            byte[] encryptedWithTag = new byte[encrypted.length + tag.length];
            System.arraycopy(encrypted, 0, encryptedWithTag, 0, encrypted.length);
            System.arraycopy(tag, 0, encryptedWithTag, encrypted.length, tag.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, decode(envelope.getString("iv"))));
            cipher.updateAAD(context.aad());
            String plaintext = new String(
                    cipher.doFinal(encryptedWithTag), StandardCharsets.UTF_8);
            JSONObject parsed = JSON.parseObject(plaintext);
            Map<String, Object> result = new LinkedHashMap<>();
            parsed.forEach(result::put);
            return Map.copyOf(result);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException(
                    "Provider Credential 解密失败，请检查密钥或 Connection 身份");
        }
    }

    @Override
    public boolean isConfigured(String ciphertext) {
        return ciphertext != null && !ciphertext.isBlank();
    }

    private void validateEnvelope(JSONObject envelope) {
        if (envelope == null
                || envelope.getIntValue("v") != ENVELOPE_VERSION
                || !ALGORITHM.equals(envelope.getString("alg"))
                || envelope.getString("kid") == null
                || envelope.getString("iv") == null
                || envelope.getString("ciphertext") == null
                || envelope.getString("tag") == null) {
            throw new ServiceException("Provider Credential 密文格式不受支持");
        }
    }

    private SecretKeySpec keyFor(String keyId) {
        String keyMaterial;
        if (keyId.equals(properties.getActiveKeyId())) {
            keyMaterial = activeKeyMaterial();
        } else {
            keyMaterial = properties.getDecryptionKeys().get(keyId);
        }
        return decodeKey(keyMaterial);
    }

    private String activeKeyMaterial() {
        return properties.getActiveKey() == null || properties.getActiveKey().isBlank()
                ? platformCredentialKey : properties.getActiveKey();
    }

    private SecretKeySpec decodeKey(String keyMaterial) {
        String material = requireText(keyMaterial, "未配置 Provider Credential 主密钥");
        try {
            byte[] decoded = Base64.getDecoder().decode(material);
            if (decoded.length == 32) {
                return new SecretKeySpec(decoded, "AES");
            }
        } catch (IllegalArgumentException ignored) {
            // 非 Base64 的平台密钥走下方 SHA-256 派生，便于复用现有安全配置。
        }
        if (material.length() < 32) {
            throw new ServiceException(
                    "Provider Credential 主密钥至少 32 字符或使用 256-bit Base64");
        }
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derived, "AES");
        } catch (Exception e) {
            throw new ServiceException("Provider Credential 主密钥派生失败");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(message);
        }
        return value.trim();
    }

    private String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }
}
