package com.polaris.ai.core.usage;

import org.springframework.context.ApplicationEvent;

/**
 * Token 消耗事件，SSE 流式对话完成时发布。
 */
public class TokenUsageEvent extends ApplicationEvent {

    private final String tenantId;
    private final int totalTokens;

    public TokenUsageEvent(String tenantId, int totalTokens) {
        super(tenantId);
        this.tenantId = tenantId;
        this.totalTokens = totalTokens;
    }

    public String getTenantId() {
        return tenantId;
    }

    public int getTotalTokens() {
        return totalTokens;
    }
}
