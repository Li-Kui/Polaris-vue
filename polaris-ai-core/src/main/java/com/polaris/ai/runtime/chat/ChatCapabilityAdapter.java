package com.polaris.ai.runtime.chat;

import com.polaris.ai.pivot.AiModelFactory;
import com.polaris.ai.runtime.CapabilityAdapter;
import com.polaris.ai.runtime.ModelExecutionResult;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.stream.*;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import com.polaris.ai.runtime.usage.UsageSource;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

/** 复用现有 LangChain4j OpenAI-compatible Runtime 的 CHAT Adapter。 */
@Component
public class ChatCapabilityAdapter
        implements CapabilityAdapter<ChatCapabilityInvocation, ChatResponse> {

    private final AiModelFactory modelFactory;

    public ChatCapabilityAdapter(AiModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    @Override
    public String capabilityCode() {
        return ChatCapabilityInvocation.CAPABILITY_CODE;
    }

    @Override
    public Class<ChatCapabilityInvocation> invocationType() {
        return ChatCapabilityInvocation.class;
    }

    @Override
    public Class<ChatResponse> resultType() {
        return ChatResponse.class;
    }

    @Override
    public ModelExecutionResult<ChatResponse> execute(
            ModelRuntimeSpec runtime,
            ChatCapabilityInvocation invocation) {
        ChatResponse response = modelFactory.createChatModel(runtime)
                .chat(invocation.request());
        return new ModelExecutionResult<>(
                response, normalizeUsage(response), providerRequestId(response));
    }

    @Override
    public Flow.Publisher<ModelStreamEvent> stream(
            ModelRuntimeSpec runtime,
            ChatCapabilityInvocation invocation) {
        if (!runtime.activeFeatures().contains("STREAMING")) {
            throw new IllegalArgumentException(
                    "STREAMING Feature 未激活");
        }
        StreamingChatModel model =
                modelFactory.createStreamingChatModel(runtime);
        return downstream -> model.chat(invocation.request())
                .subscribe(new ChatStreamBridge(downstream));
    }

    private NormalizedUsage normalizeUsage(ChatResponse response) {
        TokenUsage usage = response == null ? null : response.tokenUsage();
        if (usage == null) {
            return NormalizedUsage.unknown();
        }
        return new NormalizedUsage(
                usage.inputTokenCount(), usage.outputTokenCount(),
                usage.totalTokenCount(), null, null, null,
                java.util.Map.of(), UsageSource.PROVIDER_REPORTED);
    }

    private String providerRequestId(ChatResponse response) {
        return response == null ? null : response.id();
    }

    private List<ModelStreamEvent> map(ChatModelStreamingEvent event) {
        if (event instanceof PartialResponse response) {
            return List.of(new TextDelta(response.text()));
        }
        if (event instanceof PartialThinking thinking) {
            return List.of(new ReasoningDelta(thinking.text()));
        }
        if (event instanceof PartialToolCall toolCall) {
            return List.of(new ToolCallDelta(
                    toolCall.index(), toolCall.id(), toolCall.name(),
                    toolCall.partialArguments(), false));
        }
        if (event instanceof CompleteToolCall toolCall) {
            ToolExecutionRequest request = toolCall.toolExecutionRequest();
            return List.of(new ToolCallDelta(
                    toolCall.index(), request.id(), request.name(),
                    request.arguments(), true));
        }
        if (event instanceof CompleteResponse complete) {
            ChatResponse response = complete.chatResponse();
            List<ModelStreamEvent> events = new ArrayList<>(2);
            NormalizedUsage usage = normalizeUsage(response);
            if (usage.source() != UsageSource.UNKNOWN) {
                events.add(new UsageEvent(usage));
            }
            events.add(new CompletedEvent(
                    response != null && response.finishReason() != null
                            ? response.finishReason().name() : null,
                    providerRequestId(response)));
            return List.copyOf(events);
        }
        return List.of();
    }

    /** 每次只向 Provider 请求一个事件，并按下游 demand 缓冲一对 Usage/Completed。 */
    private final class ChatStreamBridge
            implements Flow.Subscriber<ChatModelStreamingEvent>,
            Flow.Subscription {

        private final Flow.Subscriber<? super ModelStreamEvent> downstream;
        private final ArrayDeque<ModelStreamEvent> queue = new ArrayDeque<>();
        private final AtomicInteger drainWork = new AtomicInteger();
        private Flow.Subscription upstream;
        private long demand;
        private boolean upstreamRequested;
        private boolean sourceDone;
        private boolean cancelled;
        private Throwable sourceError;

        private ChatStreamBridge(
                Flow.Subscriber<? super ModelStreamEvent> downstream) {
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
        public void onNext(ChatModelStreamingEvent event) {
            synchronized (this) {
                if (cancelled || sourceDone) {
                    return;
                }
                upstreamRequested = false;
                List<ModelStreamEvent> mapped = map(event);
                queue.addAll(mapped);
                if (event instanceof CompleteResponse) {
                    sourceDone = true;
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
            }
            drain();
        }

        @Override
        public void request(long count) {
            if (count <= 0) {
                IllegalArgumentException error = new IllegalArgumentException(
                        "Flow request 必须大于 0");
                synchronized (this) {
                    if (cancelled || sourceDone) {
                        return;
                    }
                    sourceDone = true;
                    sourceError = error;
                }
                Flow.Subscription current = upstream;
                if (current != null) {
                    current.cancel();
                }
                drain();
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

        private void drain() {
            if (drainWork.getAndIncrement() != 0) {
                return;
            }
            int missed = 1;
            while (true) {
                ModelStreamEvent next = null;
                Flow.Subscription requestFrom = null;
                Throwable terminalError = null;
                boolean terminalComplete = false;
                synchronized (this) {
                    if (cancelled) {
                        return;
                    }
                    if (demand > 0 && !queue.isEmpty()) {
                        next = queue.removeFirst();
                        demand--;
                    } else if (queue.isEmpty() && sourceDone) {
                        cancelled = true;
                        terminalError = sourceError;
                        terminalComplete = sourceError == null;
                    } else if (demand > 0 && queue.isEmpty()
                            && upstream != null && !upstreamRequested) {
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

        private long addCap(long current, long increment) {
            long result = current + increment;
            return result < 0 ? Long.MAX_VALUE : result;
        }
    }
}
