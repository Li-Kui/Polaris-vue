package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.runtime.stream.*;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 将统一 Runtime 暴露成 LangChain4j StreamingChatModel。
 *
 * <p>用于复用现有 AiServices 工具循环；每一轮请求仍通过 ChatRuntimeService，
 * 不缓存 Runtime、Credential 或 Provider Client。</p>
 */
public final class RuntimeStreamingChatModel implements StreamingChatModel {

    private final ChatRuntimeService runtimeService;
    private final Long modelId;
    private final Set<String> requestedFeatures;
    private final List<CapabilityParameterOverride> overrides;
    private final boolean streaming;

    public RuntimeStreamingChatModel(
            ChatRuntimeService runtimeService,
            Long modelId,
            Set<String> requestedFeatures,
            List<CapabilityParameterOverride> overrides) {
        this(runtimeService, modelId, requestedFeatures, overrides, true);
    }

    public RuntimeStreamingChatModel(
            ChatRuntimeService runtimeService, Long modelId,
            Set<String> requestedFeatures,
            List<CapabilityParameterOverride> overrides, boolean streaming) {
        this.runtimeService = Objects.requireNonNull(
                runtimeService, "runtimeService");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.requestedFeatures = requestedFeatures == null
                ? Set.of() : Set.copyOf(requestedFeatures);
        this.overrides = overrides == null ? List.of() : List.copyOf(overrides);
        this.streaming = streaming;
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> chat(ChatRequest request) {
        return downstream -> (streaming
                ? runtimeService.stream(modelId, request, requestedFeatures, overrides)
                : runtimeService.streamOrExecute(modelId, request, requestedFeatures, overrides, false))
                .subscribe(new EventBridge(downstream));
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest request) {
        return chat(request);
    }

    private final class EventBridge
            implements Flow.Subscriber<ModelStreamEvent>, Flow.Subscription {

        private final Flow.Subscriber<? super ChatModelStreamingEvent> downstream;
        private final ArrayDeque<ChatModelStreamingEvent> queue =
                new ArrayDeque<>();
        private final AtomicInteger drainWork = new AtomicInteger();
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();
        private final List<ToolExecutionRequest> toolRequests =
                new ArrayList<>();
        private Flow.Subscription upstream;
        private NormalizedUsage usage = NormalizedUsage.unknown();
        private long demand;
        private boolean upstreamRequested;
        private boolean sourceDone;
        private boolean businessTerminal;
        private boolean cancelled;
        private Throwable sourceError;

        private EventBridge(
                Flow.Subscriber<? super ChatModelStreamingEvent> downstream) {
            this.downstream = Objects.requireNonNull(downstream, "downstream");
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            synchronized (this) {
                if (upstream != null || cancelled) {
                    subscription.cancel();
                    return;
                }
                upstream = subscription;
            }
            downstream.onSubscribe(this);
            drain();
        }

        @Override
        public void onNext(ModelStreamEvent event) {
            synchronized (this) {
                if (cancelled || sourceDone || businessTerminal) {
                    return;
                }
                upstreamRequested = false;
                if (event instanceof ErrorEvent error) {
                    sourceDone = true;
                    sourceError = new IllegalStateException(format(error));
                } else {
                    ChatModelStreamingEvent mapped = map(event);
                    if (mapped != null) {
                        queue.add(mapped);
                    }
                    if (event instanceof CompletedEvent) {
                        businessTerminal = true;
                    }
                }
            }
            drain();
        }

        @Override
        public void onError(Throwable error) {
            synchronized (this) {
                if (cancelled || sourceDone) {
                    return;
                }
                upstreamRequested = false;
                sourceDone = true;
                sourceError = error;
            }
            drain();
        }

        @Override
        public void onComplete() {
            synchronized (this) {
                if (cancelled || sourceDone) {
                    return;
                }
                upstreamRequested = false;
                sourceDone = true;
                if (!businessTerminal) {
                    sourceError = new IllegalStateException(
                            "MODEL_RUNTIME_STREAM_TERMINAL_EVENT_MISSING");
                }
            }
            drain();
        }

        @Override
        public void request(long count) {
            if (count <= 0) {
                failDemand();
                return;
            }
            synchronized (this) {
                demand = addCap(demand, count);
            }
            drain();
        }

        @Override
        public void cancel() {
            Flow.Subscription current;
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
                queue.clear();
                current = upstream;
            }
            if (current != null) {
                current.cancel();
            }
        }

        private ChatModelStreamingEvent map(ModelStreamEvent event) {
            if (event instanceof TextDelta delta) {
                text.append(nullToEmpty(delta.text()));
                return new PartialResponse(delta.text());
            }
            if (event instanceof ReasoningDelta delta) {
                thinking.append(nullToEmpty(delta.text()));
                return new PartialThinking(delta.text());
            }
            if (event instanceof ToolCallDelta tool) {
                if (!tool.complete()) {
                    return PartialToolCall.builder()
                            .index(tool.index())
                            .id(tool.toolCallId())
                            .name(tool.toolName())
                            .partialArguments(tool.argumentsDelta())
                            .build();
                }
                ToolExecutionRequest request = ToolExecutionRequest.builder()
                        .id(tool.toolCallId())
                        .name(tool.toolName())
                        .arguments(tool.argumentsDelta())
                        .build();
                toolRequests.add(request);
                return new CompleteToolCall(tool.index(), request);
            }
            if (event instanceof UsageEvent usageEvent) {
                usage = usageEvent.usage();
                return null;
            }
            if (event instanceof CompletedEvent completed) {
                AiMessage message = AiMessage.builder()
                        .text(text.isEmpty() ? null : text.toString())
                        .thinking(thinking.isEmpty()
                                ? null : thinking.toString())
                        .toolExecutionRequests(List.copyOf(toolRequests))
                        .build();
                ChatResponse response = ChatResponse.builder()
                        .aiMessage(message)
                        .id(completed.providerRequestId())
                        .tokenUsage(tokenUsage(usage))
                        .finishReason(finishReason(completed.finishReason()))
                        .build();
                return new CompleteResponse(response);
            }
            return null;
        }

        private void drain() {
            if (drainWork.getAndIncrement() != 0) {
                return;
            }
            int missed = 1;
            while (true) {
                ChatModelStreamingEvent next = null;
                Flow.Subscription requestFrom = null;
                Throwable terminalError = null;
                boolean terminalComplete = false;
                synchronized (this) {
                    if (cancelled) {
                        return;
                    }
                    if (demand > 0 && !queue.isEmpty()) {
                        next = queue.remove();
                        demand--;
                    } else if (sourceDone && queue.isEmpty()) {
                        cancelled = true;
                        terminalError = sourceError;
                        terminalComplete = sourceError == null;
                    } else if (demand > 0 && queue.isEmpty()
                            && !upstreamRequested && !businessTerminal
                            && upstream != null) {
                        upstreamRequested = true;
                        requestFrom = upstream;
                    }
                }
                if (next != null) {
                    downstream.onNext(next);
                    continue;
                }
                if (terminalError != null) {
                    downstream.onError(terminalError);
                    return;
                }
                if (terminalComplete) {
                    downstream.onComplete();
                    return;
                }
                if (requestFrom != null) {
                    requestFrom.request(1);
                }
                missed = drainWork.addAndGet(-missed);
                if (missed == 0) {
                    return;
                }
                missed = 1;
            }
        }

        private void failDemand() {
            Flow.Subscription current;
            synchronized (this) {
                if (cancelled || sourceDone) {
                    return;
                }
                sourceDone = true;
                sourceError = new IllegalArgumentException(
                        "Flow request 必须大于 0");
                current = upstream;
            }
            if (current != null) {
                current.cancel();
            }
            drain();
        }
    }

    private TokenUsage tokenUsage(NormalizedUsage value) {
        if (value == null || value.source()
                == com.polaris.ai.runtime.usage.UsageSource.UNKNOWN) {
            return null;
        }
        return new TokenUsage(
                value.inputTokens(), value.outputTokens(), value.totalTokens());
    }

    private FinishReason finishReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return FinishReason.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return FinishReason.OTHER;
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private long addCap(long current, long count) {
        long total = current + count;
        return total < 0 ? Long.MAX_VALUE : total;
    }
}
