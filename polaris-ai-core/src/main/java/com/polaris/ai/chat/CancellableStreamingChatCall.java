package com.polaris.ai.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.*;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 将 LangChain4j 响应式模型流适配为可主动取消的调用句柄。
 *
 * <p>该适配器只负责单次模型调用，不处理工具调用轮次。完成、失败和取消
 * 共享同一个终态门闩，避免底层晚到事件造成重复回调。</p>
 */
public final class CancellableStreamingChatCall
{
    private final StreamingChatResponseHandler handler;
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean terminal = new AtomicBoolean(false);

    private CancellableStreamingChatCall(StreamingChatResponseHandler handler)
    {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    public static CancellableStreamingChatCall start(
            StreamingChatModel model,
            List<ChatMessage> messages,
            StreamingChatResponseHandler handler)
    {
        Objects.requireNonNull(model, "model");
        CancellableStreamingChatCall call = new CancellableStreamingChatCall(handler);
        try {
            model.chat(messages).subscribe(call.new ModelSubscriber());
        } catch (RuntimeException error) {
            call.signalError(error);
        }
        return call;
    }

    /** 主动取消底层订阅，并将取消作为本次调用的唯一失败终态。 */
    public void cancel()
    {
        cancelled.set(true);
        cancelSubscription();
        signalError(new CancellationException("模型流式调用已取消"));
    }

    public boolean isCancelled()
    {
        return cancelled.get();
    }

    public boolean isTerminated()
    {
        return terminal.get();
    }

    private void cancelSubscription()
    {
        Flow.Subscription current = subscription.get();
        if (current != null) {
            current.cancel();
        }
    }

    private void signalComplete(CompleteResponse response)
    {
        if (terminal.compareAndSet(false, true)) {
            handler.onCompleteResponse(response.chatResponse());
        }
    }

    private void signalError(Throwable error)
    {
        if (terminal.compareAndSet(false, true)) {
            handler.onError(error);
        }
    }

    private final class ModelSubscriber implements Flow.Subscriber<ChatModelStreamingEvent>
    {
        @Override
        public void onSubscribe(Flow.Subscription newSubscription)
        {
            if (!subscription.compareAndSet(null, newSubscription)) {
                newSubscription.cancel();
                return;
            }
            if (cancelled.get()) {
                newSubscription.cancel();
                return;
            }
            newSubscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ChatModelStreamingEvent event)
        {
            if (terminal.get() || cancelled.get()) {
                return;
            }
            try {
                if (event instanceof PartialResponse response) {
                    handler.onPartialResponse(response.text());
                } else if (event instanceof PartialThinking thinking) {
                    handler.onPartialThinking(thinking);
                } else if (event instanceof PartialToolCall toolCall) {
                    handler.onPartialToolCall(toolCall);
                } else if (event instanceof CompleteToolCall toolCall) {
                    handler.onCompleteToolCall(toolCall);
                } else if (event instanceof RawStreamingEvent rawEvent) {
                    handler.onUnmappedRawEvent(rawEvent.rawEvent());
                } else if (event instanceof CompleteResponse response) {
                    signalComplete(response);
                }
            } catch (RuntimeException error) {
                cancelled.set(true);
                cancelSubscription();
                signalError(error);
            }
        }

        @Override
        public void onError(Throwable error)
        {
            signalError(error);
        }

        @Override
        public void onComplete()
        {
            if (!terminal.get()) {
                signalError(new IllegalStateException("模型流结束但未返回完整响应"));
            }
        }
    }
}
