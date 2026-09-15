package com.polaris.ai.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.*;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
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
    private final Executor callbackExecutor;
    private final ConcurrentLinkedQueue<Runnable> callbackQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean callbackRunning = new AtomicBoolean(false);
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean sourceTerminal = new AtomicBoolean(false);
    private final AtomicBoolean terminal = new AtomicBoolean(false);

    private CancellableStreamingChatCall(
            StreamingChatResponseHandler handler, Executor callbackExecutor)
    {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.callbackExecutor = callbackExecutor == null ? Runnable::run : callbackExecutor;
    }

    public static CancellableStreamingChatCall start(
            StreamingChatModel model,
            List<ChatMessage> messages,
            StreamingChatResponseHandler handler)
    {
        return start(model, messages, handler, null);
    }

    /**
     * 启动可取消模型流，并按原始顺序把回调卸载到指定业务线程池。
     */
    public static CancellableStreamingChatCall start(
            StreamingChatModel model,
            List<ChatMessage> messages,
            StreamingChatResponseHandler handler,
            Executor callbackExecutor)
    {
        Objects.requireNonNull(model, "model");
        CancellableStreamingChatCall call = new CancellableStreamingChatCall(handler, callbackExecutor);
        try {
            model.chat(messages).subscribe(call.new ModelSubscriber());
        } catch (RuntimeException error) {
            call.sourceTerminal.set(true);
            call.dispatch(() -> call.signalError(error));
        }
        return call;
    }

    /** 使用已经包含请求参数的 Publisher 启动调用，供声明式工具代理复用。 */
    public static CancellableStreamingChatCall start(
            Flow.Publisher<ChatModelStreamingEvent> publisher,
            StreamingChatResponseHandler handler,
            Executor callbackExecutor)
    {
        Objects.requireNonNull(publisher, "publisher");
        CancellableStreamingChatCall call = new CancellableStreamingChatCall(handler, callbackExecutor);
        try {
            publisher.subscribe(call.new ModelSubscriber());
        } catch (RuntimeException error) {
            call.sourceTerminal.set(true);
            call.dispatch(() -> call.signalError(error));
        }
        return call;
    }

    /** 主动取消底层订阅，并将取消作为本次调用的唯一失败终态。 */
    public void cancel()
    {
        cancelled.set(true);
        sourceTerminal.set(true);
        cancelSubscription();
        dispatch(() -> signalError(new CancellationException("模型流式调用已取消")));
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

    /** 共享线程池可能并发执行任务，这里用串行队列保持模型事件原始顺序。 */
    private void dispatch(Runnable callback)
    {
        callbackQueue.add(callback);
        if (!callbackRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            callbackExecutor.execute(this::drainCallbacks);
        } catch (RuntimeException rejected) {
            drainCallbacks();
        }
    }

    private void drainCallbacks()
    {
        try {
            Runnable callback;
            while ((callback = callbackQueue.poll()) != null) {
                callback.run();
            }
        } finally {
            callbackRunning.set(false);
            if (!callbackQueue.isEmpty()) {
                dispatch(() -> { });
            }
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
            if (sourceTerminal.get() || terminal.get() || cancelled.get()) {
                return;
            }
            if (event instanceof CompleteResponse) {
                sourceTerminal.set(true);
            }
            dispatch(() -> handleEvent(event));
        }

        private void handleEvent(ChatModelStreamingEvent event)
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
                sourceTerminal.set(true);
                cancelSubscription();
                signalError(error);
            }
        }

        @Override
        public void onError(Throwable error)
        {
            sourceTerminal.set(true);
            dispatch(() -> signalError(error));
        }

        @Override
        public void onComplete()
        {
            if (sourceTerminal.compareAndSet(false, true)) {
                dispatch(() -> signalError(new IllegalStateException("模型流结束但未返回完整响应")));
            }
        }
    }
}
