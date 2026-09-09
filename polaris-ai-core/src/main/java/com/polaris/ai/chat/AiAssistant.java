package com.polaris.ai.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.TokenStream;

import java.util.List;

/**
 * AI 助手代理接口 —— 用于 LangChain4j 的声明式服务代理
 *
 * @author polaris
 */
public interface AiAssistant
{
    /**
     * 流式对话接口
     *
     * @param messages 历史对话上下文
     * @return 响应 Token 流
     */
    TokenStream chat(List<ChatMessage> messages);
}
