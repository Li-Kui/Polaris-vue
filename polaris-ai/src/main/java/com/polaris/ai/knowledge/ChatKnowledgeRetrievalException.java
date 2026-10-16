package com.polaris.ai.knowledge;

/** 仅接收对话知识库适配器生成的固定安全提示，不携带提供器异常及载荷。 */
public class ChatKnowledgeRetrievalException extends RuntimeException {
    public ChatKnowledgeRetrievalException(String message) {
        super(message);
    }
}
