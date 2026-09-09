package com.polaris.ai.core.usage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Token 用量跟踪器。
 * 用于 SSE 流式对话中统计 Token 消耗，完成后触发计费事件。
 */
@Component
public class TokenUsageTracker {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageTracker.class);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * SSE 完成时触发计费
     */
    public void onStreamComplete(String tenantId, int totalTokens) {
        if (tenantId == null || totalTokens <= 0) return;
        try {
            eventPublisher.publishEvent(new TokenUsageEvent(tenantId, totalTokens));
            log.debug("租户 {} Token 消耗: {}", tenantId, totalTokens);
        } catch (Exception e) {
            log.warn("发布 Token 计费事件失败: {}", e.getMessage());
        }
    }

}
