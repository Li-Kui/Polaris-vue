package com.polaris.ai.observability;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/** 在不改变模型行为的前提下采集请求、首 Token、Token 用量和成功率。 */
public final class ObservedStreamingChatModel implements StreamingChatModel, AutoCloseable
{
    private final StreamingChatModel delegate;
    private final String provider;
    private final String model;

    public ObservedStreamingChatModel(
            StreamingChatModel delegate, String provider, String model)
    {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.provider = provider;
        this.model = model;
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler)
    {
        delegate.chat(request, observed(handler));
    }

    @Override
    public void chat(
            ChatRequest request,
            ChatRequestOptions options,
            StreamingChatResponseHandler handler)
    {
        delegate.chat(request, options, observed(handler));
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler)
    {
        delegate.doChat(request, observed(handler));
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> chat(ChatRequest request)
    {
        return observed(delegate.chat(request));
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest request)
    {
        return observed(delegate.doChat(request));
    }

    private StreamingChatResponseHandler observed(StreamingChatResponseHandler handler)
    {
        long startedAt = AiObservability.start();
        AtomicBoolean firstToken = new AtomicBoolean(false);
        AtomicBoolean terminal = new AtomicBoolean(false);
        return new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse)
            {
                if (firstToken.compareAndSet(false, true)) {
                    AiObservability.recordFirstToken(provider, model, startedAt);
                }
                handler.onPartialResponse(partialResponse);
            }

            @Override
            public void onPartialThinking(
                    dev.langchain4j.model.chat.response.PartialThinking partialThinking)
            {
                handler.onPartialThinking(partialThinking);
            }

            @Override
            public void onPartialToolCall(
                    dev.langchain4j.model.chat.response.PartialToolCall partialToolCall)
            {
                handler.onPartialToolCall(partialToolCall);
            }

            @Override
            public void onCompleteToolCall(
                    dev.langchain4j.model.chat.response.CompleteToolCall completeToolCall)
            {
                handler.onCompleteToolCall(completeToolCall);
            }

            @Override
            public void onUnmappedRawEvent(Object rawEvent)
            {
                handler.onUnmappedRawEvent(rawEvent);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse)
            {
                if (terminal.compareAndSet(false, true)) {
                    AiObservability.recordChat(
                            provider, model, startedAt, completeResponse, null);
                }
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error)
            {
                if (terminal.compareAndSet(false, true)) {
                    AiObservability.recordChat(provider, model, startedAt, null, error);
                }
                handler.onError(error);
            }
        };
    }

    private Flow.Publisher<ChatModelStreamingEvent> observed(
            Flow.Publisher<ChatModelStreamingEvent> publisher)
    {
        return subscriber -> {
            long startedAt = AiObservability.start();
            AtomicBoolean firstToken = new AtomicBoolean(false);
            AtomicBoolean terminal = new AtomicBoolean(false);
            publisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription)
                {
                    subscriber.onSubscribe(new Flow.Subscription() {
                        @Override
                        public void request(long count)
                        {
                            subscription.request(count);
                        }

                        @Override
                        public void cancel()
                        {
                            if (terminal.compareAndSet(false, true)) {
                                AiObservability.recordChat(
                                        provider, model, startedAt, null,
                                        new java.util.concurrent.CancellationException(
                                                "模型流式调用已取消"));
                            }
                            subscription.cancel();
                        }
                    });
                }

                @Override
                public void onNext(ChatModelStreamingEvent event)
                {
                    if (event instanceof dev.langchain4j.model.chat.response.PartialResponse
                            && firstToken.compareAndSet(false, true)) {
                        AiObservability.recordFirstToken(provider, model, startedAt);
                    }
                    if (event instanceof CompleteResponse response
                            && terminal.compareAndSet(false, true)) {
                        AiObservability.recordChat(
                                provider, model, startedAt, response.chatResponse(), null);
                    }
                    subscriber.onNext(event);
                }

                @Override
                public void onError(Throwable error)
                {
                    if (terminal.compareAndSet(false, true)) {
                        AiObservability.recordChat(provider, model, startedAt, null, error);
                    }
                    subscriber.onError(error);
                }

                @Override
                public void onComplete()
                {
                    if (terminal.compareAndSet(false, true)) {
                        AiObservability.recordChat(
                                provider, model, startedAt, null,
                                new IllegalStateException("模型流结束但未返回完整响应"));
                    }
                    subscriber.onComplete();
                }
            });
        };
    }

    @Override
    public ChatRequestParameters defaultRequestParameters()
    {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners()
    {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider()
    {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities()
    {
        return delegate.supportedCapabilities();
    }

    @Override
    public void close() throws Exception
    {
        if (delegate instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
