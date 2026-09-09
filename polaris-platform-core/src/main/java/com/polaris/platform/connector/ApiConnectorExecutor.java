package com.polaris.platform.connector;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.polaris.platform.domain.PlatformApiConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 第三方 API 连接器执行器
 */
@Slf4j
@Component
public class ApiConnectorExecutor {

    private static final long MAX_RESPONSE_BYTES = 1024L * 1024L;
    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie",
            "host", "content-length", "connection", "transfer-encoding");

    private final ConnectorHttpSafetyPolicy safetyPolicy;
    private final ConnectorCredentialCipher credentialCipher;

    public ApiConnectorExecutor(
            ConnectorHttpSafetyPolicy safetyPolicy,
            ConnectorCredentialCipher credentialCipher) {
        this.safetyPolicy = safetyPolicy;
        this.credentialCipher = credentialCipher;
    }

    public ResponseEntity<String> execute(PlatformApiConnector connector, String path, HttpMethod method, Object body, Map<String, String> queryParams) {
        String url = buildUrl(connector.getBaseUrl(), path, queryParams);
        safetyPolicy.validate(url);
        HttpHeaders headers = buildHeaders(connector);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        try {
            RestTemplate restTemplate = new RestTemplate(requestFactory(connector.getTimeoutMs()));
            restTemplate.getInterceptors().add((request, requestBody, execution) ->
                    bounded(execution.execute(request, requestBody)));
            return restTemplate.exchange(url, method, entity, String.class);
        } catch (Exception e) {
            log.error("API 连接器请求失败: target={}, error={}", safeTarget(url), e.getClass().getSimpleName());
            throw new RuntimeException("连接器调用失败");
        }
    }

    private String buildUrl(String baseUrl, String path, Map<String, String> queryParams) {
        URI base = safetyPolicy.validate(baseUrl);
        String url = base.toString();
        if (path != null && !path.isEmpty()) {
            if (!url.endsWith("/") && !path.startsWith("/")) {
                url += "/" + path;
            } else if (url.endsWith("/") && path.startsWith("/")) {
                url += path.substring(1);
            } else {
                url += path;
            }
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        if (queryParams != null) {
            queryParams.forEach(builder::queryParam);
        }
        return builder.build().encode().toUriString();
    }

    private HttpHeaders buildHeaders(PlatformApiConnector connector) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 注入默认 Headers
        if (connector.getDefaultHeaders() != null && !connector.getDefaultHeaders().isEmpty()) {
            try {
                JSONObject json = JSON.parseObject(connector.getDefaultHeaders());
                json.forEach((k, v) -> {
                    if (k != null && !BLOCKED_HEADERS.contains(k.toLowerCase(Locale.ROOT))) {
                        headers.set(k, String.valueOf(v));
                    }
                });
            } catch (Exception ignored) {}
        }

        // 注入认证
        String authType = connector.getAuthType();
        String decryptedAuthConfig = credentialCipher.decrypt(connector.getAuthConfig());
        if ("API_KEY".equalsIgnoreCase(authType) && decryptedAuthConfig != null) {
            JSONObject auth = JSON.parseObject(decryptedAuthConfig);
            String headerName = auth.getString("headerName");
            String apiKey = auth.getString("apiKey");
            if (headerName != null && apiKey != null) {
                headers.set(headerName, apiKey);
            }
        } else if ("BEARER".equalsIgnoreCase(authType) && decryptedAuthConfig != null) {
            JSONObject auth = JSON.parseObject(decryptedAuthConfig);
            String token = auth.getString("token");
            if (token != null) {
                headers.setBearerAuth(token);
            }
        }

        return headers;
    }

    private SimpleClientHttpRequestFactory requestFactory(Integer configuredTimeout) {
        int timeout = configuredTimeout == null ? 30_000
                : Math.max(1_000, Math.min(configuredTimeout, 120_000));
        SimpleClientHttpRequestFactory factory = new NoRedirectRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    private ClientHttpResponse bounded(ClientHttpResponse response) throws IOException {
        long contentLength = response.getHeaders().getContentLength();
        if (contentLength > MAX_RESPONSE_BYTES) {
            response.close();
            throw new IOException("连接器响应超过1MB限制");
        }
        return new ClientHttpResponse() {
            @Override
            public HttpStatusCode getStatusCode() throws IOException {
                return response.getStatusCode();
            }

            @Override
            public String getStatusText() throws IOException {
                return response.getStatusText();
            }

            @Override
            public void close() {
                response.close();
            }

            @Override
            public InputStream getBody() throws IOException {
                return new LimitedInputStream(response.getBody(), MAX_RESPONSE_BYTES);
            }

            @Override
            public HttpHeaders getHeaders() {
                return response.getHeaders();
            }
        };
    }

    private String safeTarget(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() < 0 ? "" : ":" + uri.getPort())
                    + (uri.getPath() == null ? "" : uri.getPath());
        } catch (Exception e) {
            return "invalid-target";
        }
    }

    private static class LimitedInputStream extends FilterInputStream {
        private final long maximum;
        private long count;

        LimitedInputStream(InputStream input, long maximum) {
            super(input);
            this.maximum = maximum;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) count(1);
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int value = super.read(bytes, offset, length);
            if (value > 0) count(value);
            return value;
        }

        private void count(int value) throws IOException {
            count += value;
            if (count > maximum) {
                throw new IOException("连接器响应超过1MB限制");
            }
        }
    }

    private static class NoRedirectRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod)
                throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }
}
