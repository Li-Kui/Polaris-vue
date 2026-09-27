package com.polaris.ai.modelcenter.client;

import com.polaris.ai.modelcenter.protocol.ProtocolHttpMethod;
import com.polaris.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 以 connectionId:revision 为键短期复用安全 Provider HTTP Client。 */
@Component
public class DefaultProviderHttpClientFactory
        implements ProviderHttpClientFactory {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int MAX_CACHE_ENTRIES = 512;

    private final ProviderErrorTranslator errorTranslator;
    private final ConcurrentMap<ClientCacheKey, CacheEntry> cache =
            new ConcurrentHashMap<>();

    public DefaultProviderHttpClientFactory(ProviderErrorTranslator errorTranslator) {
        this.errorTranslator = errorTranslator;
    }

    @Override
    public ProviderHttpClient get(ProviderRuntimeContext context) {
        validateContext(context);
        long now = System.currentTimeMillis();
        ClientCacheKey key = new ClientCacheKey(
                context.connectionId(), context.connectionRevision());
        CacheEntry cached = cache.get(key);
        String identity = identity(context);
        if (cached != null && cached.expiresAtMillis() > now) {
            if (!cached.identity().equals(identity)) {
                throw new ServiceException(
                        "PROVIDER_CONNECTION_IDENTITY_MISMATCH");
            }
            return cached.client();
        }
        prune(now);
        ProviderHttpClient client = new JdkProviderHttpClient(context, errorTranslator);
        cache.put(key, new CacheEntry(
                identity, client, now + CACHE_TTL.toMillis()));
        return client;
    }

    private void validateContext(ProviderRuntimeContext context) {
        if (context == null || context.connectionId() == null
                || context.connectionId() <= 0
                || context.connectionRevision() < 1
                || blank(context.providerCode()) || blank(context.protocolCode())
                || blank(context.networkMode()) || blank(context.baseUrl())) {
            throw new ServiceException("Provider Runtime Context 无效");
        }
    }

    private void prune(long now) {
        cache.entrySet().removeIf(entry ->
                entry.getValue().expiresAtMillis() <= now);
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
    }

    private String identity(ProviderRuntimeContext context) {
        return String.join("|", context.providerCode(), context.protocolCode(),
                context.networkMode(), context.baseUrl());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ClientCacheKey(Long connectionId, long connectionRevision) {
    }

    private record CacheEntry(
            String identity,
            ProviderHttpClient client,
            long expiresAtMillis) {
    }

    private static final class JdkProviderHttpClient
            implements ProviderHttpClient {

        private static final Logger log = LoggerFactory.getLogger(
                JdkProviderHttpClient.class);
        private static final List<String> REQUEST_ID_HEADERS = List.of(
                "x-request-id", "request-id", "x-amzn-requestid", "cf-ray");

        private final ProviderRuntimeContext context;
        private final ProviderErrorTranslator errorTranslator;
        private final HttpClient client;

        private JdkProviderHttpClient(
                ProviderRuntimeContext context,
                ProviderErrorTranslator errorTranslator) {
            this.context = context;
            this.errorTranslator = errorTranslator;
            this.client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
        }

        @Override
        public ProviderHttpResponse execute(ProviderHttpRequest request) {
            long started = System.nanoTime();
            try {
                URI endpoint = URI.create(endpoint(request));
                HttpRequest httpRequest = buildRequest(endpoint, request);
                HttpResponse<InputStream> response = client.send(
                        httpRequest, HttpResponse.BodyHandlers.ofInputStream());
                String body;
                try (InputStream input = response.body()) {
                    byte[] bytes = input.readNBytes(request.maxResponseBytes() + 1);
                    if (bytes.length > request.maxResponseBytes()) {
                        throw new ProviderCallException(
                                ProviderErrorType.PROVIDER_UNAVAILABLE,
                                response.statusCode(), requestId(response),
                                elapsedMillis(started));
                    }
                    body = new String(bytes, StandardCharsets.UTF_8);
                }
                ProviderHttpResponse result = new ProviderHttpResponse(
                        response.statusCode(), body, requestId(response),
                        elapsedMillis(started));
                log.info("Provider HTTP call provider={}, status={}, requestId={}, latencyMs={}",
                        context.providerCode(), result.statusCode(),
                        result.providerRequestId(), result.latencyMillis());
                return result;
            } catch (ProviderCallException e) {
                logFailure(e);
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ProviderCallException translated = new ProviderCallException(
                        ProviderErrorType.UNKNOWN, null, null,
                        elapsedMillis(started));
                logFailure(translated);
                throw translated;
            } catch (Exception e) {
                ProviderCallException translated = errorTranslator.fromTransport(
                        e, elapsedMillis(started));
                logFailure(translated);
                throw translated;
            }
        }

        private HttpRequest buildRequest(
                URI endpoint,
                ProviderHttpRequest request) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(request.timeout())
                    .header("Accept", "application/json");
            String token = credential();
            builder.header("Authorization", "Bearer " + token);
            if (request.endpoint().method() == ProtocolHttpMethod.GET) {
                return builder.GET().build();
            }
            byte[] body = request.jsonBody().getBytes(StandardCharsets.UTF_8);
            if (body.length > request.maxRequestBytes()) {
                throw new ProviderCallException(
                        ProviderErrorType.REQUEST_INVALID, null, null, 0);
            }
            return builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
        }

        private String credential() {
            Object value = context.credentials().get("apiKey");
            if (!(value instanceof String token) || token.isBlank()) {
                throw new ProviderCallException(
                        ProviderErrorType.AUTH_FAILED, null, null, 0);
            }
            return token;
        }

        private String endpoint(ProviderHttpRequest request) {
            String base = context.baseUrl().endsWith("/")
                    ? context.baseUrl().substring(
                    0, context.baseUrl().length() - 1)
                    : context.baseUrl();
            return base + request.endpoint().relativePath();
        }

        private String requestId(HttpResponse<?> response) {
            for (String header : REQUEST_ID_HEADERS) {
                String value = response.headers().firstValue(header).orElse(null);
                if (value != null) {
                    value = value.trim();
                    if (!value.isEmpty() && value.length() <= 128
                            && value.codePoints().noneMatch(Character::isISOControl)) {
                        return value;
                    }
                }
            }
            return null;
        }

        private long elapsedMillis(long started) {
            return Duration.ofNanos(System.nanoTime() - started).toMillis();
        }

        private void logFailure(ProviderCallException error) {
            log.warn("Provider HTTP call failed provider={}, status={}, requestId={}, type={}, latencyMs={}",
                    context.providerCode(), error.httpStatus(),
                    error.providerRequestId(), error.errorType(),
                    error.latencyMillis());
        }

        @Override
        public String toString() {
            return "JdkProviderHttpClient[providerCode=" + context.providerCode()
                    + ", connectionId=" + context.connectionId()
                    + ", connectionRevision=" + context.connectionRevision()
                    + ", credential=<redacted>]";
        }
    }
}
