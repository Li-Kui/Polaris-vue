package com.polaris.platform.tenant;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;

import java.util.Objects;

/**
 * 中台租户归属校验工具类。
 */
public final class PlatformTenantGuard {

    private PlatformTenantGuard() {
    }

    public static Long requireTenantId() {
        CallerContext context = CallerContextHolder.require();
        if (!context.isPlatformMode()) {
            throw new IllegalStateException("当前调用不是中台模式");
        }

        String tenantId = context.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("中台模式缺少租户ID");
        }
        try {
            long value = Long.parseLong(tenantId);
            if (value <= 0) {
                throw new IllegalStateException("中台租户ID格式错误");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("中台租户ID格式错误");
        }
    }

    public static boolean belongsToCurrentTenant(Long resourceTenantId) {
        return resourceTenantId != null && Objects.equals(requireTenantId(), resourceTenantId);
    }
}
