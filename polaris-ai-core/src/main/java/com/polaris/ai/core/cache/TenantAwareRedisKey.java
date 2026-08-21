package com.polaris.ai.core.cache;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;

/**
 * 租户感知的 Redis Key 生成器。
 * 中台模式下自动追加租户前缀，避免多租户缓存冲突。
 */
public final class TenantAwareRedisKey {

    private TenantAwareRedisKey() {}

    /**
     * 构建租户感知的 Redis Key。
     * 管理后台模式: 返回原始 key
     * 中台模式: 返回 key + ":tenant:" + tenantId
     */
    public static String build(String baseKey) {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx != null && ctx.getTenantId() != null) {
            return baseKey + ":tenant:" + ctx.getTenantId();
        }
        return baseKey;
    }

    /**
     * 构建指定租户的 Redis Key
     */
    public static String build(String baseKey, String tenantId) {
        if (tenantId != null) {
            return baseKey + ":tenant:" + tenantId;
        }
        return baseKey;
    }
}
