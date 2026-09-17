package com.polaris.ai.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 为声明式 AI Services 提供可主动取消的模型装饰器。
 *
 * <p>工具调用可能产生多轮模型请求。本装饰器始终记录当前一轮的底层订阅，
 * 取消后既终止当前请求，也阻止后续工具轮次继续请求模型。</p>
 */
public final class CancellableStreamingChatModel implements StreamingChatModel
{
    private final StreamingChatModel delegate;
    private final Executor callbackExecutor;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<CancellableStreamingChatCall> activeCall = new AtomicReference<>();

    public CancellableStreamingChatModel(
            StreamingChatModel delegate, Executor callbackExecutor)
    {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.callbackExecutor = callbackExecutor;
    }

    /** 取消当前模型轮次，并阻止声明式代理继续发起下一轮请求。 */
    public void cancel()
    {
        cancelled.set(true);
        CancellableStreamingChatCall call = activeCall.getAndSet(null);
        if (call != null && !call.isTerminated()) {
            call.cancel();
        }
    }

    public boolean isCancelled()
    {
        return cancelled.get();
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler)
    {
        if (cancelled.get()) {
            handler.onError(new CancellationException("模型流式调用已取消"));
            return;
        }
        AtomicReference<CancellableStreamingChatCall> roundRef = new AtomicReference<>();
        StreamingChatResponseHandler guardedHandler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse)
            {
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
            public void onCompleteResponse(
                    dev.langchain4j.model.chat.response.ChatResponse completeResponse)
            {
                clearRound(roundRef);
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error)
            {
                clearRound(roundRef);
                handler.onError(error);
            }
        };
        CancellableStreamingChatCall call = CancellableStreamingChatCall.start(
                delegate.chat(request), guardedHandler, callbackExecutor);
        roundRef.set(call);
        CancellableStreamingChatCall previous = activeCall.getAndSet(call);
        if (previous != null && previous != call && !previous.isTerminated()) {
            previous.cancel();
        }
        if (cancelled.get()) {
            cancel();
        } else if (call.isTerminated()) {
            activeCall.compareAndSet(call, null);
        }
    }

    private void clearRound(AtomicReference<CancellableStreamingChatCall> roundRef)
    {
        CancellableStreamingChatCall call = roundRef.get();
        if (call != null) {
            activeCall.compareAndSet(call, null);
        }
    }

    @Override
    public void chat(
            ChatRequest request,
            ChatRequestOptions options,
            StreamingChatResponseHandler handler)
    {
        // AI Services 当前使用不带 options 的入口；显式 options 调用继续交由原模型处理。
        delegate.chat(request, options, handler);
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler)
    {
        chat(request, handler);
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> chat(ChatRequest request)
    {
        return delegate.chat(request);
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest request)
    {
        return delegate.doChat(request);
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
    public void chat(String userMessage, StreamingChatResponseHandler handler)
    {
        delegate.chat(userMessage, handler);
    }

    @Override
    public void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler)
    {
        delegate.chat(messages, handler);
    }

    @Override
    public Flow.Publisher<String> chat(String userMessage)
    {
        return delegate.chat(userMessage);
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> chat(ChatMessage... messages)
    {
        return delegate.chat(messages);
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> chat(List<ChatMessage> messages)
    {
        return delegate.chat(messages);
    }

    @Override
    public Set<Capability> supportedCapabilities()
    {
        return delegate.supportedCapabilities();
    }
}
