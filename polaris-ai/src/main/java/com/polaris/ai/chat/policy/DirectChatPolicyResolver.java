package com.polaris.ai.chat.policy;

/** 解析普通直接聊天的 Prompt、历史、工具与搜索策略。 */
public interface DirectChatPolicyResolver {

    DirectChatPolicy resolve(Long modelId, Long userId);
}
