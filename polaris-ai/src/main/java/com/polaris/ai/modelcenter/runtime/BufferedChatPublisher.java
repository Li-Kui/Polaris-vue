package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.runtime.ModelExecutionResult;
import com.polaris.ai.runtime.stream.*;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayDeque;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** 将非流式回复适配为同一业务事件链，保留工具、思考、用量和取消语义。 */
final class BufferedChatPublisher implements Flow.Publisher<ModelStreamEvent> {
    private final Supplier<ModelExecutionResult<ChatResponse>> call;

    BufferedChatPublisher(Supplier<ModelExecutionResult<ChatResponse>> call) {
        this.call = call;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ModelStreamEvent> subscriber) {
        subscriber.onSubscribe(new Flow.Subscription() {
            private final AtomicInteger draining = new AtomicInteger();
            private final ArrayDeque<ModelStreamEvent> events = new ArrayDeque<>();
            private volatile boolean cancelled;
            private boolean initialized;
            private long demand;

            @Override
            public void request(long count) {
                if (cancelled) return;
                if (count <= 0) {
                    cancelled = true;
                    subscriber.onError(new IllegalArgumentException("请求数量必须大于零"));
                    return;
                }
                synchronized (this) {
                    demand = demand > Long.MAX_VALUE - count ? Long.MAX_VALUE : demand + count;
                }
                if (draining.getAndIncrement() != 0) return;
                int pending = 1;
                do {
                    try {
                        if (!initialized && !cancelled) {
                            initialized = true;
                            var result = call.get();
                            var response = result.value();
                            var message = response.aiMessage();
                            if ((message.text() == null || message.text().isBlank())
                                    && (message.toolExecutionRequests() == null
                                    || message.toolExecutionRequests().isEmpty())) {
                                throw new com.polaris.common.exception.ServiceException(
                                        "MODEL_EMPTY_FINAL_ANSWER");
                            }
                            if (message.thinking() != null) events.add(new ReasoningDelta(message.thinking()));
                            if (message.text() != null) events.add(new TextDelta(message.text()));
                            if (message.toolExecutionRequests() != null) {
                                int index = 0;
                                for (var tool : message.toolExecutionRequests()) {
                                    events.add(new ToolCallDelta(index++, tool.id(), tool.name(), tool.arguments(), true));
                                }
                            }
                            events.add(new UsageEvent(result.usage()));
                            events.add(new CompletedEvent(response.finishReason() == null
                                    ? "STOP" : response.finishReason().name(), result.providerRequestId()));
                        }
                        while (!cancelled && !events.isEmpty()) {
                            synchronized (this) {
                                if (demand == 0) break;
                                demand--;
                            }
                            subscriber.onNext(events.remove());
                        }
                        if (!cancelled && initialized && events.isEmpty()) {
                            cancelled = true;
                            subscriber.onComplete();
                        }
                    } catch (Throwable error) {
                        if (!cancelled) {
                            cancelled = true;
                            subscriber.onError(error);
                        }
                    }
                    pending = draining.addAndGet(-pending);
                } while (pending != 0);
            }

            @Override
            public void cancel() { cancelled = true; }
        });
    }
}
