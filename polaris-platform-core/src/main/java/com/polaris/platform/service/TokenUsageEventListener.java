package com.polaris.platform.service;

import com.polaris.ai.core.usage.TokenUsageEvent;
import com.polaris.platform.mapper.TenantMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Token 计费消耗异步监听器
 */
@Slf4j
@Component
public class TokenUsageEventListener {

    @Autowired
    private TenantMapper tenantMapper;

    @Async("aiTaskExecutor")
    @EventListener
    public void onTokenUsage(TokenUsageEvent event) {
        try {
            if (event.getTenantId() == null || event.getTotalTokens() <= 0) {
                return;
            }
            Long tenantId = Long.parseLong(event.getTenantId());
            tenantMapper.updateUsedTokens(tenantId, event.getTotalTokens());
            log.info("已记录租户[{}] Token 消耗: {} 个", tenantId, event.getTotalTokens());
        } catch (Exception e) {
            log.error("更新租户 Token 用量失败, tenantId={}", event.getTenantId(), e);
        }
    }
}
