package com.polaris.ai.runtime;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

/** 显式 QA 调用的无正文传输观测；异步回调持有单次实例，不从工作线程读取上下文。 */
public final class ModelHttpDiagnostics {
    private static final ThreadLocal<ModelHttpDiagnostics> CURRENT = new ThreadLocal<>();
    private static final long MAX_COUNT = 1_000_000_000L;
    private long startedNanos;
    private boolean instrumented, frozen, cancellationRequested, responseHeadersReceived;
    private boolean bodyCompleted, transportFailed, countersSaturated;
    private long requestAttempts, bodyChunks, bodyBytes, sseEvents;
    private Long firstRequestMs, headersMs, firstBodyByteMs, firstSseEventMs;
    private Integer httpStatus;
    private final ModelSseFrameDiagnostics frameClasses = new ModelSseFrameDiagnostics();

    public record Snapshot(int version, boolean instrumented, long requestAttempts, Long firstRequestMs,
                           boolean responseHeadersReceived, Integer httpStatus, Long headersMs,
                           Long firstBodyByteMs, long bodyChunks, long bodyBytes, long sseEvents,
                           Long firstSseEventMs, boolean bodyCompleted, boolean transportFailed,
                           boolean cancellationRequested, boolean countersSaturated, boolean frozen,
                           ModelSseFrameDiagnostics.Snapshot frameClasses) { }

    public static ModelHttpDiagnostics current() { return CURRENT.get(); }

    /** 只覆盖同步客户端构建／订阅窗口，不使用 InheritableThreadLocal，不改变其他请求。 */
    public static Scope bind(ModelHttpDiagnostics diagnostics) {
        return new Scope(java.util.Objects.requireNonNull(diagnostics));
    }

    public static final class Scope implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final ModelHttpDiagnostics previous, bound;
        private boolean closed;
        private Scope(ModelHttpDiagnostics bound) {
            previous = CURRENT.get();this.bound = bound;CURRENT.set(bound);
        }
        @Override public void close() {
            if (closed) return;
            if (owner != Thread.currentThread() || CURRENT.get() != bound) {
                throw new IllegalStateException("HTTP_DIAGNOSTIC_SCOPE_ORDER");
            }
            closed = true;
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public synchronized long start() {
        if (startedNanos == 0) startedNanos = System.nanoTime();
        return startedNanos;
    }
    public synchronized void freeze(boolean cancelRequested) {
        if (!frozen) { cancellationRequested = cancelRequested;frozen = true; }
    }
    public synchronized Snapshot snapshot() {
        return new Snapshot(1, instrumented, requestAttempts, firstRequestMs, responseHeadersReceived,
                httpStatus, headersMs, firstBodyByteMs, bodyChunks, bodyBytes, sseEvents, firstSseEventMs,
                bodyCompleted, transportFailed, cancellationRequested, countersSaturated, frozen, frameClasses.snapshot());
    }
    private long elapsed() { return startedNanos == 0 ? 0 : Math.max(0, (System.nanoTime()-startedNanos)/1_000_000); }
    private long add(long current, long amount) {
        if (amount > MAX_COUNT-current) { countersSaturated = true;return MAX_COUNT; }
        return current+amount;
    }
    private synchronized void request() {
        if (frozen) return;
        requestAttempts = add(requestAttempts, 1);
        if (firstRequestMs == null) firstRequestMs = elapsed();
    }
    private synchronized void headers(int status) {
        if (frozen) return;
        responseHeadersReceived = true;
        if (headersMs == null) { headersMs = elapsed();httpStatus = status >= 100 && status <= 599 ? status : null; }
    }
    private synchronized void body(List<ByteBuffer> buffers) {
        if (frozen) return;
        long size = 0;
        for (ByteBuffer buffer : buffers) size += buffer.remaining();
        if (size == 0) return;
        if (firstBodyByteMs == null) firstBodyByteMs = elapsed();
        bodyChunks = add(bodyChunks, 1);bodyBytes = add(bodyBytes, size);
    }
    private synchronized void sse(ServerSentEvent event) {
        if (frozen) return;
        if (firstSseEventMs == null) firstSseEventMs = elapsed();
        sseEvents = add(sseEvents, 1);
        frameClasses.accept(event.data());
    }
    private synchronized void completed() { if (!frozen) bodyCompleted = true; }
    private synchronized void failed() { if (!frozen) transportFailed = true; }

    public synchronized HttpClient.Builder instrument(HttpClient.Builder builder) {
        if (!frozen) instrumented = true;
        return new ObservedBuilder(builder);
    }

    /** SDK 解析之后、模型映射之前统计 SSE 帧；不读取 event/data，更不记录正文。 */
    public JdkHttpClient client(JdkHttpClientBuilder builder) {
        return new JdkHttpClient(builder) {
            @Override public Flow.Publisher<HttpStreamingEvent> stream(
                    dev.langchain4j.http.client.HttpRequest request, ServerSentEventParser parser) {
                var source = super.stream(request, parser);
                return downstream -> source.subscribe(new Flow.Subscriber<>() {
                    @Override public void onSubscribe(Flow.Subscription subscription) { downstream.onSubscribe(subscription); }
                    @Override public void onNext(HttpStreamingEvent event) {
                        if (event instanceof ServerSentEvent sseEvent) sse(sseEvent);
                        downstream.onNext(event);
                    }
                    @Override public void onError(Throwable error) { downstream.onError(error); }
                    @Override public void onComplete() { downstream.onComplete(); }
                });
            }
        };
    }

    private <T> HttpResponse.BodyHandler<T> observe(HttpResponse.BodyHandler<T> handler) {
        return info -> {
            headers(info.statusCode());
            var downstream = handler.apply(info);
            return new HttpResponse.BodySubscriber<>() {
                @Override public CompletionStage<T> getBody() { return downstream.getBody(); }
                @Override public void onSubscribe(Flow.Subscription subscription) { downstream.onSubscribe(subscription); }
                @Override public void onNext(List<ByteBuffer> buffers) { body(buffers);downstream.onNext(buffers); }
                @Override public void onError(Throwable error) { failed();downstream.onError(error); }
                @Override public void onComplete() { completed();downstream.onComplete(); }
            };
        };
    }

    private final class ObservedClient extends HttpClient {
        private final HttpClient delegate;
        private ObservedClient(HttpClient delegate) { this.delegate = delegate; }
        @Override public Optional<CookieHandler> cookieHandler() { return delegate.cookieHandler(); }
        @Override public Optional<Duration> connectTimeout() { return delegate.connectTimeout(); }
        @Override public Redirect followRedirects() { return delegate.followRedirects(); }
        @Override public Optional<ProxySelector> proxy() { return delegate.proxy(); }
        @Override public SSLContext sslContext() { return delegate.sslContext(); }
        @Override public SSLParameters sslParameters() { return delegate.sslParameters(); }
        @Override public Optional<Authenticator> authenticator() { return delegate.authenticator(); }
        @Override public Version version() { return delegate.version(); }
        @Override public Optional<Executor> executor() { return delegate.executor(); }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException, InterruptedException {
            ModelHttpDiagnostics.this.request();
            try { return delegate.send(request, observe(handler)); }
            catch (IOException | InterruptedException | RuntimeException error) { failed();throw error; }
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            return sendAsync(request, handler, null);
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> push) {
            ModelHttpDiagnostics.this.request();
            try {
                var future = push == null ? delegate.sendAsync(request, observe(handler))
                        : delegate.sendAsync(request, observe(handler), push);
                // 返回原 Future：取消必须继续传播，不能换成 whenComplete 产生的依赖 Future。
                future.whenComplete((response, error) -> { if (error != null) failed(); });
                return future;
            } catch (RuntimeException error) { failed();throw error; }
        }
    }

    private final class ObservedBuilder implements HttpClient.Builder {
        private final HttpClient.Builder delegate;
        private ObservedBuilder(HttpClient.Builder delegate) { this.delegate = delegate; }
        @Override public HttpClient.Builder cookieHandler(CookieHandler value) { delegate.cookieHandler(value);return this; }
        @Override public HttpClient.Builder connectTimeout(Duration value) { delegate.connectTimeout(value);return this; }
        @Override public HttpClient.Builder sslContext(SSLContext value) { delegate.sslContext(value);return this; }
        @Override public HttpClient.Builder sslParameters(SSLParameters value) { delegate.sslParameters(value);return this; }
        @Override public HttpClient.Builder executor(Executor value) { delegate.executor(value);return this; }
        @Override public HttpClient.Builder followRedirects(HttpClient.Redirect value) { delegate.followRedirects(value);return this; }
        @Override public HttpClient.Builder version(HttpClient.Version value) { delegate.version(value);return this; }
        @Override public HttpClient.Builder priority(int value) { delegate.priority(value);return this; }
        @Override public HttpClient.Builder proxy(ProxySelector value) { delegate.proxy(value);return this; }
        @Override public HttpClient.Builder authenticator(Authenticator value) { delegate.authenticator(value);return this; }
        @Override public HttpClient build() { return new ObservedClient(delegate.build()); }
    }
}
