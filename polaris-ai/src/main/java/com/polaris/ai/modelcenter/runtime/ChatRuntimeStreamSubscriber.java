package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.runtime.stream.*;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** 将统一 Runtime 的内部 CHAT 事件适配为 Direct Chat 业务回调。 */
public final class ChatRuntimeStreamSubscriber
        implements Flow.Subscriber<ModelStreamEvent> {

    private final Consumer<String> textConsumer;
    private final Consumer<String> reasoningConsumer;
    private final Consumer<Integer> completionConsumer;
    private final Consumer<Throwable> errorConsumer;
    private final Consumer<Runnable> cancellationRegistrar;
    private final AtomicBoolean terminal = new AtomicBoolean();
    private Flow.Subscription subscription;
    private Integer totalTokens;

    public ChatRuntimeStreamSubscriber(
            Consumer<String> textConsumer,
            Consumer<String> reasoningConsumer,
            Consumer<Integer> completionConsumer,
            Consumer<Throwable> errorConsumer,
            Consumer<Runnable> cancellationRegistrar) {
        this.textConsumer = Objects.requireNonNull(textConsumer, "textConsumer");
        this.reasoningConsumer = Objects.requireNonNull(
                reasoningConsumer, "reasoningConsumer");
        this.completionConsumer = Objects.requireNonNull(
                completionConsumer, "completionConsumer");
        this.errorConsumer = Objects.requireNonNull(errorConsumer, "errorConsumer");
        this.cancellationRegistrar = Objects.requireNonNull(
                cancellationRegistrar, "cancellationRegistrar");
    }

    @Override
    public void onSubscribe(Flow.Subscription candidate) {
        Objects.requireNonNull(candidate, "subscription");
        if (subscription != null || terminal.get()) {
            candidate.cancel();
            return;
        }
        subscription = candidate;
        cancellationRegistrar.accept(candidate::cancel);
        candidate.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(ModelStreamEvent event) {
        if (event == null || terminal.get()) {
            return;
        }
        try {
            if (event instanceof TextDelta text) {
                textConsumer.accept(text.text());
            } else if (event instanceof ReasoningDelta reasoning) {
                reasoningConsumer.accept(reasoning.text());
            } else if (event instanceof UsageEvent usage) {
                totalTokens = usage.usage().totalTokens();
            } else if (event instanceof CompletedEvent) {
                complete();
            } else if (event instanceof ErrorEvent error) {
                fail(new IllegalStateException(format(error)));
            }
        } catch (RuntimeException | Error error) {
            Flow.Subscription current = subscription;
            if (current != null) {
                current.cancel();
            }
            fail(error);
        }
    }

    @Override
    public void onError(Throwable error) {
        fail(error == null
                ? new IllegalStateException("MODEL_RUNTIME_STREAM_ERROR")
                : error);
    }

    @Override
    public void onComplete() {
        if (!terminal.get()) {
            fail(new IllegalStateException(
                    "MODEL_RUNTIME_STREAM_TERMINAL_EVENT_MISSING"));
        }
    }

    private void complete() {
        if (terminal.compareAndSet(false, true)) {
            completionConsumer.accept(totalTokens);
        }
    }

    private void fail(Throwable error) {
        if (terminal.compareAndSet(false, true)) {
            errorConsumer.accept(error);
        }
    }

    private String format(ErrorEvent error) {
        return "MODEL_RUNTIME_STREAM_ERROR[category="
                + safe(error.errorCategory()) + ", code="
                + safe(error.errorCode()) + ", retryable="
                + error.retryable() + "]";
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
