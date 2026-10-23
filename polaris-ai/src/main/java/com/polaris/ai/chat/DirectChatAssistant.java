package com.polaris.ai.chat;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.service.TokenStream;

import java.util.List;

/** 本轮多模态输入；历史由请求级 ChatMemory 传递，不能将 ChatMessage 列表字符串化。 */
public interface DirectChatAssistant {
    TokenStream chat(List<Content> contents);
}
