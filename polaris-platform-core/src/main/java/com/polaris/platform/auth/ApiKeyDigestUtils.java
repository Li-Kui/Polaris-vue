package com.polaris.platform.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * API Key 摘要工具类
 */
public final class ApiKeyDigestUtils {

    private static final int KEY_PREFIX_LENGTH = 11;

    private ApiKeyDigestUtils() {
    }

    /**
     * 计算 API Key 的 SHA-256 摘要
     */
    public static String digest(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前环境不支持 SHA-256", e);
        }
    }

    /**
     * 获取用于列表脱敏展示的密钥前缀
     */
    public static String prefix(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        return apiKey.substring(0, Math.min(KEY_PREFIX_LENGTH, apiKey.length()));
    }
}
