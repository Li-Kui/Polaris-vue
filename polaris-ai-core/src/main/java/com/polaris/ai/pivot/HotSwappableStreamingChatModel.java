package com.polaris.ai.pivot;

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
import java.util.concurrent.Flow;
import java.util.function.Supplier;

/**
 * 每次调用时重新解析当前生效的流式对话模型。
 *
 * <p>Spring Bean 本身保持稳定，模型配置可以在运行时刷新。完整转发
 * LangChain4j 1.20 的调用入口与模型元数据，避免调用方意外落入接口默认实现。</p>
 */
public final class HotSwappableStreamingChatModel implements StreamingChatModel
{

    private final Supplier<StreamingChatModel> delegateSupplier;

    public HotSwappableStreamingChatModel(Supplier<StreamingChatModel> delegateSupplier)
    {
        this.delegateSupplier = Objects.requireNonNull(delegateSupplier, "delegateSupplier");
    }

    private StreamingChatModel delegate()
    {
        return Objects.requireNonNull(delegateSupplier.get(), "active streaming chat model");
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler)
    {
        delegate().chat(request, handler);
    }

    @Override
    public void chat(ChatRequest request, ChatRequestOptions options,
                     StreamingChatResponseHandler handler)
    {
        delegate().chat(request, options, handler);
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler)
    {
        delegate().doChat(request, handler);
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> chat(ChatRequest request)
    {
        return delegate().chat(request);
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest request)
    {
        return delegate().doChat(request);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters()
    {
        return delegate().defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners()
    {
        return delegate().listeners();
    }

    @Override
    public ModelProvider provider()
    {
        return delegate().provider();
    }

    @Override
    public void chat(String userMessage, StreamingChatResponseHandler handler)
    {
        delegate().chat(userMessage, handler);
    }

    @Override
    public void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler)
    {
        delegate().chat(messages, handler);
    }

    @Override
    public Flow.Publisher<String> chat(String userMessage)
    {
        return delegate().chat(userMessage);
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> chat(ChatMessage... messages)
    {
        return delegate().chat(messages);
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> chat(List<ChatMessage> messages)
    {
        return delegate().chat(messages);
    }

    @Override
    public Set<Capability> supportedCapabilities()
    {
        return delegate().supportedCapabilities();
    }
}
