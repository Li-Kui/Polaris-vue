package com.polaris.platform.mcp;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

/**
 * SDK 2.0 的内部 sendAsync 订阅不完全随调用方取消而结束。
 * 每次探测独占一个客户端，同时跟踪 HTTP future 和 SSE body subscription。
 */
final class McpManagedHttpClient extends HttpClient implements AutoCloseable {
    private final HttpClient delegate;
    private final Set<CompletableFuture<?>> requests = new HashSet<>();
    private final Set<Flow.Subscription> bodies = new HashSet<>();
    private boolean closed;
    private static final long MAX_BODY_BYTES = 256 * 1024;

    private McpManagedHttpClient(HttpClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
            HttpResponse.BodyHandler<T> handler) {
        if (closed) {
            return CompletableFuture.failedFuture(new IOException("MCP_HTTP_CLIENT_CLOSED"));
        }
        CompletableFuture<HttpResponse<T>> future = delegate.sendAsync(request,
                response -> trackBody(handler.apply(response), "POST".equals(request.method()) ? response.statusCode() : 200));
        requests.add(future);
        future.whenComplete((result, error) -> removeRequest(future));
        return future;
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
            HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushHandler) {
        // 第一阶段只使用 HTTP/1.1；不接受服务端推送引入额外目标。
        if (pushHandler != null) {
            return CompletableFuture.failedFuture(new IOException("MCP_HTTP_PUSH_NOT_SUPPORTED"));
        }
        return sendAsync(request, handler);
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        throw new UnsupportedOperationException("MCP_HTTP_ASYNC_ONLY");
    }

    private synchronized void removeRequest(CompletableFuture<?> future) {
        requests.remove(future);
    }

    private synchronized void removeBody(Flow.Subscription subscription) {
        bodies.remove(subscription);
    }

    private <T> HttpResponse.BodySubscriber<T> trackBody(HttpResponse.BodySubscriber<T> subscriber, int statusCode) {
        return new HttpResponse.BodySubscriber<>() {
            private Flow.Subscription subscription;
            private long received;
            private boolean failed;

            @Override
            public CompletionStage<T> getBody() {
                return subscriber.getBody();
            }

            @Override
            public void onSubscribe(Flow.Subscription upstream) {
                subscription = upstream;
                synchronized (McpManagedHttpClient.this) {
                    if (closed) {
                        upstream.cancel();
                        subscriber.onSubscribe(new Flow.Subscription() {
                            public void request(long count) { }
                            public void cancel() { }
                        });
                        subscriber.onError(new IOException("MCP_HTTP_CLIENT_CLOSED"));
                        return;
                    }
                    bodies.add(upstream);
                }
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long count) {
                        upstream.request(count);
                    }

                    @Override
                    public void cancel() {
                        removeBody(upstream);
                        upstream.cancel();
                    }
                });
                if (statusCode >= 300) {
                    fail(new McpInvocationException(statusCode == 401 || statusCode == 403
                            ? McpInvocationException.Code.AUTHENTICATION_FAILED : McpInvocationException.Code.HTTP_ERROR));
                }
            }

            @Override
            public void onNext(List<ByteBuffer> buffers) {
                if (failed) return;
                for (ByteBuffer buffer : buffers) received += buffer.remaining();
                if (received > MAX_BODY_BYTES) {
                    fail(new McpInvocationException(McpInvocationException.Code.RESULT_TOO_LARGE));
                    return;
                }
                subscriber.onNext(buffers);
            }

            private void fail(Throwable error) {
                failed = true;
                removeBody(subscription);
                subscription.cancel();
                subscriber.onError(error);
            }

            @Override
            public void onError(Throwable error) {
                removeBody(subscription);
                if (failed) return;
                subscriber.onError(error);
            }

            @Override
            public void onComplete() {
                removeBody(subscription);
                if (failed) return;
                subscriber.onComplete();
            }
        };
    }

    @Override
    public void close() {
        Set<CompletableFuture<?>> pending;
        Set<Flow.Subscription> streaming;
        synchronized (this) {
            closed = true;
            pending = new HashSet<>(requests);
            streaming = new HashSet<>(bodies);
            requests.clear();
            bodies.clear();
        }
        pending.forEach(future -> future.cancel(true));
        streaming.forEach(Flow.Subscription::cancel);
    }

    @Override public Optional<CookieHandler> cookieHandler() { return delegate.cookieHandler(); }
    @Override public Optional<Duration> connectTimeout() { return delegate.connectTimeout(); }
    @Override public Redirect followRedirects() { return delegate.followRedirects(); }
    @Override public Optional<ProxySelector> proxy() { return delegate.proxy(); }
    @Override public SSLContext sslContext() { return delegate.sslContext(); }
    @Override public SSLParameters sslParameters() { return delegate.sslParameters(); }
    @Override public Optional<Authenticator> authenticator() { return delegate.authenticator(); }
    @Override public Version version() { return delegate.version(); }
    @Override public Optional<Executor> executor() { return delegate.executor(); }

    /** 适配 SDK 的 builder 注入接口，仍使用 JDK 默认 TLS 证书校验。 */
    static final class ManagedBuilder implements HttpClient.Builder, AutoCloseable {
        private final HttpClient.Builder delegate = HttpClient.newBuilder()
                .version(Version.HTTP_1_1).followRedirects(Redirect.NEVER);
        private McpManagedHttpClient client;

        @Override public Builder cookieHandler(CookieHandler value) { delegate.cookieHandler(value); return this; }
        @Override public Builder connectTimeout(Duration value) { delegate.connectTimeout(value); return this; }
        @Override public Builder sslContext(SSLContext value) { delegate.sslContext(value); return this; }
        @Override public Builder sslParameters(SSLParameters value) { delegate.sslParameters(value); return this; }
        @Override public Builder executor(Executor value) { delegate.executor(value); return this; }
        @Override public Builder followRedirects(Redirect value) { delegate.followRedirects(value); return this; }
        @Override public Builder version(Version value) { delegate.version(value); return this; }
        @Override public Builder priority(int value) { delegate.priority(value); return this; }
        @Override public Builder proxy(ProxySelector value) { delegate.proxy(value); return this; }
        @Override public Builder authenticator(Authenticator value) { delegate.authenticator(value); return this; }

        @Override
        public HttpClient build() {
            if (client != null) {
                throw new IllegalStateException("MCP_HTTP_CLIENT_ALREADY_BUILT");
            }
            client = new McpManagedHttpClient(delegate.build());
            return client;
        }

        @Override
        public void close() {
            if (client != null) {
                client.close();
            }
        }
    }
}
