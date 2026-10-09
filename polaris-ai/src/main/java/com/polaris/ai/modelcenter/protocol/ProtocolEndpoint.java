package com.polaris.ai.modelcenter.protocol;

import java.util.Objects;
import java.util.regex.Pattern;

/** 由代码固定的相对端点，不允许 Profile 注入 URL、Query 或路径穿越。 */
public record ProtocolEndpoint(
        ProtocolHttpMethod method,
        String relativePath) {

    private static final Pattern SAFE_PATH = Pattern.compile(
            "^/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+$");

    public ProtocolEndpoint {
        method = Objects.requireNonNull(method, "method");
        if (relativePath == null || relativePath.isBlank()
                || !relativePath.startsWith("/")
                || relativePath.length() > 160
                || !SAFE_PATH.matcher(relativePath).matches()
                || relativePath.contains("://")
                || relativePath.contains("?")
                || relativePath.contains("#")
                || relativePath.contains("\\")
                || relativePath.contains("..")
                || relativePath.contains("//")) {
            throw new IllegalArgumentException("Protocol Endpoint 必须是安全相对路径");
        }
    }
}
