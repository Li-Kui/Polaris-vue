package com.polaris.ai.customer;

import com.polaris.ai.runtime.ModelExecutionResult;
import com.polaris.ai.runtime.stream.*;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import com.polaris.common.exception.ServiceException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 在原调用线程消费有界流，权限/审核保持原租户上下文；退出时取消上游，不重试。 */
final class CustomerAnswerStream implements Flow.Subscriber<ModelStreamEvent> {
    private final ArrayBlockingQueue<ModelStreamEvent> events = new ArrayBlockingQueue<>(4);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private volatile Flow.Subscription subscription;
    private volatile boolean ended;

    @Override public void onSubscribe(Flow.Subscription value) {
        if (subscription != null) { value.cancel(); return; }
        subscription = value;
        value.request(1);
    }
    @Override public void onNext(ModelStreamEvent event) {
        if (!events.offer(event)) { failure.set(new IllegalStateException()); subscription.cancel(); }
    }
    @Override public void onError(Throwable error) { failure.set(error); ended = true; }
    @Override public void onComplete() { ended = true; }

    ModelExecutionResult<ChatResponse> read(Flow.Publisher<ModelStreamEvent> publisher, BooleanSupplier active,
            Consumer<NormalizedUsage> usage, Consumer<String> progress) {
        var text = new StringBuilder();
        NormalizedUsage measured = NormalizedUsage.unknown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        long nextCheck = 0;
        try {
            publisher.subscribe(this);
            while (System.nanoTime() < deadline) {
                if (System.nanoTime() >= nextCheck) {
                    if (!active.getAsBoolean()) throw new ServiceException("ANSWER_INVALID: 本轮回答已停止", 409);
                    nextCheck = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);
                }
                if (failure.get() != null) throw failed();
                var event = events.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) {
                    if (ended) throw failed();
                    continue;
                }
                if (event instanceof TextDelta delta) {
                    if (delta.text() == null || text.length() + delta.text().length() > 4000) throw invalid();
                    text.append(delta.text());
                    progress.accept(text.toString());
                } else if (event instanceof UsageEvent value) {
                    measured = value.usage(); usage.accept(measured);
                } else if (event instanceof ToolCallDelta) throw invalid();
                else if (event instanceof ErrorEvent) throw failed();
                else if (event instanceof CompletedEvent completed) {
                    if (text.isEmpty() || "TOOL_EXECUTION".equals(completed.finishReason())) throw invalid();
                    return new ModelExecutionResult<>(ChatResponse.builder().aiMessage(AiMessage.from(text.toString())).build(),
                            measured, completed.providerRequestId());
                }
                subscription.request(1);
            }
            throw failed();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw failed();
        } finally { if (subscription != null) subscription.cancel(); }
    }
    private ServiceException failed() { return new ServiceException("MODEL_CALL_FAILED: 回答流未完整结束，请核对原请求", 502); }
    private ServiceException invalid() { return new ServiceException("ANSWER_INVALID: 回答内容或长度无效", 502); }
}
