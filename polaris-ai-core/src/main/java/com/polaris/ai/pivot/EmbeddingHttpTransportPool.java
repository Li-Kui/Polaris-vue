package com.polaris.ai.pivot;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/** 仅供直连向量请求复用无凭据传输；SDK、请求正文、授权和读取预算仍逐请求创建。 */
final class EmbeddingHttpTransportPool {
    private static final int MAX_CLIENTS = 8;
    private final Map<Duration,HttpClient> clients = new LinkedHashMap<>();

    HttpClient.Builder wrap(HttpClient.Builder delegate) { return new Builder(delegate); }

    private synchronized HttpClient build(Duration connectTimeout, HttpClient.Builder delegate) {
        var existing = clients.get(connectTimeout);
        if (existing != null) return existing;
        var created = delegate.build();
        if (clients.size() < MAX_CLIENTS) clients.put(connectTimeout, created);
        return created;
    }

    private final class Builder implements HttpClient.Builder {
        private final HttpClient.Builder delegate;
        private Duration connectTimeout;
        private boolean reusable = true;
        private Builder(HttpClient.Builder delegate) { this.delegate = delegate; }
        @Override public HttpClient.Builder connectTimeout(Duration value) { delegate.connectTimeout(value);connectTimeout=value;return this; }
        @Override public HttpClient build() { return reusable && connectTimeout != null ? EmbeddingHttpTransportPool.this.build(connectTimeout,delegate) : delegate.build(); }
        // 若 SDK 将来增加其他传输配置，保留该配置并退出缓存，不复用不完整的键。
        @Override public HttpClient.Builder cookieHandler(CookieHandler value) { reusable=false;delegate.cookieHandler(value);return this; }
        @Override public HttpClient.Builder sslContext(SSLContext value) { reusable=false;delegate.sslContext(value);return this; }
        @Override public HttpClient.Builder sslParameters(SSLParameters value) { reusable=false;delegate.sslParameters(value);return this; }
        @Override public HttpClient.Builder executor(Executor value) { reusable=false;delegate.executor(value);return this; }
        @Override public HttpClient.Builder followRedirects(HttpClient.Redirect value) { reusable=false;delegate.followRedirects(value);return this; }
        @Override public HttpClient.Builder version(HttpClient.Version value) { reusable=false;delegate.version(value);return this; }
        @Override public HttpClient.Builder priority(int value) { reusable=false;delegate.priority(value);return this; }
        @Override public HttpClient.Builder proxy(ProxySelector value) { reusable=false;delegate.proxy(value);return this; }
        @Override public HttpClient.Builder authenticator(Authenticator value) { reusable=false;delegate.authenticator(value);return this; }
    }
}
