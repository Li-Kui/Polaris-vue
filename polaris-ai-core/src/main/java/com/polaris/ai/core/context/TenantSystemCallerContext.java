package com.polaris.ai.core.context;

/**
 * 租户内部任务调用上下文，用于定时触发和外部事件等脱离请求线程的执行。
 */
public class TenantSystemCallerContext implements CallerContext {

    private final Long tenantId;

    public TenantSystemCallerContext(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("租户标识必须大于零");
        }
        this.tenantId = tenantId;
    }

    @Override
    public Long getUserId() {
        return 0L;
    }

    @Override
    public String getTenantId() {
        return String.valueOf(tenantId);
    }

    @Override
    public String getUsername() {
        return "system:tenant:" + tenantId;
    }

    @Override
    public Long getDeptId() {
        return null;
    }

    @Override
    public boolean isSuperAdmin() {
        return false;
    }

    @Override
    public boolean isTenantAdmin() {
        return true;
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }
}
