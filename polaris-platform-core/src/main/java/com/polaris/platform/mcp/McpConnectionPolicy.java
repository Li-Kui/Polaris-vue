package com.polaris.platform.mcp;

import com.polaris.common.exception.ServiceException;
import com.polaris.platform.connector.ConnectorHttpSafetyPolicy;
import com.polaris.platform.domain.PlatformMcpConnection;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** MCP 专用端点与凭证规则，实际 HTTP 请求仍需逐次校验公网地址。 */
@Component
public class McpConnectionPolicy {
    private static final Pattern HEADER_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9-]{0,99}");
    private static final Set<String> RESERVED_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "host",
            "connection", "content-type", "content-length", "transfer-encoding",
            "accept", "origin", "referer", "upgrade", "expect", "te", "trailer",
            "last-event-id", "platform-token");

    private final ConnectorHttpSafetyPolicy httpSafetyPolicy;

    public McpConnectionPolicy(ConnectorHttpSafetyPolicy httpSafetyPolicy) {
        this.httpSafetyPolicy = httpSafetyPolicy;
    }

    public URI validate(PlatformMcpConnection connection) {
        String url = connection.getEndpointUrl();
        if (url == null || url.isBlank() || url.length() > 2048) {
            throw new ServiceException("MCP端点不能为空或超过2048个字符");
        }
        URI uri;
        try {
            uri = URI.create(url).normalize();
        } catch (IllegalArgumentException e) {
            throw new ServiceException("MCP端点格式无效");
        }
        if (uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new ServiceException("MCP端点不能包含用户信息、查询参数或片段，凭证请使用请求头配置");
        }
        if (uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new ServiceException("MCP端点端口无效");
        }
        String authType = connection.getAuthType();
        if (!Set.of("NONE", "API_KEY", "BEARER").contains(authType == null ? "" : authType)) {
            throw new ServiceException("不支持的MCP认证类型");
        }
        if (!"NONE".equals(authType) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ServiceException("携带凭证的MCP端点必须使用HTTPS");
        }
        if ("API_KEY".equals(authType)) {
            String name = connection.getCredentialHeaderName();
            String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
            if (name == null || !HEADER_NAME.matcher(name).matches()
                    || RESERVED_HEADERS.contains(lower) || lower.startsWith("mcp-")
                    || lower.startsWith("sec-") || lower.startsWith("proxy-")) {
                throw new ServiceException("MCP API Key请求头名称无效或与协议保留头冲突");
            }
        } else if (connection.getCredentialHeaderName() != null) {
            throw new ServiceException("只有API_KEY认证允许设置凭证请求头名");
        }
        if (connection.getTimeoutMs() == null || connection.getTimeoutMs() < 1000
                || connection.getTimeoutMs() > 60000) {
            throw new ServiceException("MCP总超时必须在1000到60000毫秒之间");
        }
        if (!Set.of("0", "1").contains(connection.getStatus() == null ? "" : connection.getStatus())) {
            throw new ServiceException("MCP连接状态无效");
        }
        // 不携带 URL 原文抛出异常，防止异常信息回显用户误填的敏感内容。
        try {
            return httpSafetyPolicy.validate(uri.toString());
        } catch (IllegalArgumentException e) {
            throw new ServiceException("MCP端点必须是可解析的公网HTTP/HTTPS地址");
        }
    }

    public void validateSecret(String secret) {
        if (secret == null || secret.isBlank() || secret.length() > 4096
                || secret.chars().anyMatch(value -> value < 32 || value > 126)) {
            throw new ServiceException("MCP凭证不能为空、包含控制字符或超过4096个字符");
        }
    }
}
