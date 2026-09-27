package com.polaris.ai.chat.policy;

import com.polaris.ai.pivot.AiModelProperties;
import com.polaris.ai.prompt.SystemPromptResolver;
import org.springframework.stereotype.Service;

/**
 * Direct Chat 的应用级策略来源。
 *
 * <p>Prompt 与历史窗口属于聊天产品策略，不再存放于模型定义；工具能力由
 * Agent 或本次请求声明，Provider 凭据只由统一 Runtime 解析。</p>
 */
@Service
public class DefaultDirectChatPolicyResolver
        implements DirectChatPolicyResolver {

    private final AiModelProperties modelProperties;
    private final SystemPromptResolver promptResolver;

    public DefaultDirectChatPolicyResolver(
            AiModelProperties modelProperties,
            SystemPromptResolver promptResolver) {
        this.modelProperties = modelProperties;
        this.promptResolver = promptResolver;
    }

    @Override
    public DirectChatPolicy resolve(Long modelId, Long userId) {
        int maxHistory = Math.max(
                0, modelProperties.getMaxHistoryMessages());
        return new DirectChatPolicy(
                promptResolver.getRoleSpecificSystemPrompt(userId),
                maxHistory,
                null,
                null);
    }
}
