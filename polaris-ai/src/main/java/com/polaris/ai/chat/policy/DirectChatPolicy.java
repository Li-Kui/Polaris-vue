package com.polaris.ai.chat.policy;

/**
 * Direct Chat 自身的业务策略，不属于 Model Center Runtime 参数。
 *
 * <p>策略来自聊天应用配置，而不是 Provider 模型定义。</p>
 */
public record DirectChatPolicy(
        String systemPrompt,
        int maxHistoryMessages,
        String enabledTools,
        String searchKey) {

    @Override
    public String toString() {
        return "DirectChatPolicy[maxHistoryMessages=" + maxHistoryMessages
                + ", hasSystemPrompt=" + hasText(systemPrompt)
                + ", hasEnabledTools=" + hasText(enabledTools)
                + ", hasSearchKey=" + hasText(searchKey) + "]";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
