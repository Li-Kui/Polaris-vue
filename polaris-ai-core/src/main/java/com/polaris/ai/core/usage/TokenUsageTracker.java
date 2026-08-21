package com.polaris.ai.core.usage;

import com.polaris.ai.core.cache.TenantAwareRedisKey;
import com.polaris.common.core.redis.RedisCache;
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

    private static final String USED_TOKENS_KEY = "platform:tenant:used_tokens";
    private static final String QUOTA_KEY = "platform:tenant:quota_tokens";

    @Autowired(required = false)
    private RedisCache redisCache;

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

    /**
     * 检查租户配额是否已用尽
     */
    public boolean isQuotaExceeded(String tenantId) {
        if (tenantId == null || redisCache == null) return false;
        try {
            Long used = redisCache.getCacheObject(TenantAwareRedisKey.build(USED_TOKENS_KEY, tenantId));
            Long quota = redisCache.getCacheObject(TenantAwareRedisKey.build(QUOTA_KEY, tenantId));
            if (quota == null || quota == -1) return false;
            return used != null && used >= quota;
        } catch (Exception e) {
            log.warn("检查租户配额异常: {}", e.getMessage());
            return false;
        }
    }
}
