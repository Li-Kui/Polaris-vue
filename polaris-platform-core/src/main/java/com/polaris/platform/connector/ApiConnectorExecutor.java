package com.polaris.platform.connector;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.polaris.platform.domain.PlatformApiConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 第三方 API 连接器执行器
 */
@Slf4j
@Component
public class ApiConnectorExecutor {

    private final RestTemplate restTemplate = new RestTemplate();

    public ResponseEntity<String> execute(PlatformApiConnector connector, String path, HttpMethod method, Object body, Map<String, String> queryParams) {
        String url = buildUrl(connector.getBaseUrl(), path, queryParams);
        HttpHeaders headers = buildHeaders(connector);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        try {
            return restTemplate.exchange(url, method, entity, String.class);
        } catch (Exception e) {
            log.error("API 连接器请求失败: url={}", url, e);
            throw new RuntimeException("连接器调用失败: " + e.getMessage());
        }
    }

    private String buildUrl(String baseUrl, String path, Map<String, String> queryParams) {
        String url = baseUrl;
        if (path != null && !path.isEmpty()) {
            if (!url.endsWith("/") && !path.startsWith("/")) {
                url += "/" + path;
            } else if (url.endsWith("/") && path.startsWith("/")) {
                url += path.substring(1);
            } else {
                url += path;
            }
        }
        if (queryParams != null && !queryParams.isEmpty()) {
            StringBuilder sb = new StringBuilder(url);
            sb.append(url.contains("?") ? "&" : "?");
            queryParams.forEach((k, v) -> sb.append(k).append("=").append(v).append("&"));
            url = sb.substring(0, sb.length() - 1);
        }
        return url;
    }

    private HttpHeaders buildHeaders(PlatformApiConnector connector) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 注入默认 Headers
        if (connector.getDefaultHeaders() != null && !connector.getDefaultHeaders().isEmpty()) {
            try {
                JSONObject json = JSON.parseObject(connector.getDefaultHeaders());
                json.forEach((k, v) -> headers.add(k, String.valueOf(v)));
            } catch (Exception ignored) {}
        }

        // 注入认证
        String authType = connector.getAuthType();
        if ("API_KEY".equalsIgnoreCase(authType) && connector.getAuthConfig() != null) {
            JSONObject auth = JSON.parseObject(connector.getAuthConfig());
            String headerName = auth.getString("headerName");
            String apiKey = auth.getString("apiKey");
            if (headerName != null && apiKey != null) {
                headers.add(headerName, apiKey);
            }
        } else if ("BEARER".equalsIgnoreCase(authType) && connector.getAuthConfig() != null) {
            JSONObject auth = JSON.parseObject(connector.getAuthConfig());
            String token = auth.getString("token");
            if (token != null) {
                headers.setBearerAuth(token);
            }
        }

        return headers;
    }
}
