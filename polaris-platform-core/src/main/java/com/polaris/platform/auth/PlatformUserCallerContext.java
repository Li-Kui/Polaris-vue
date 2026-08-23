package com.polaris.platform.auth;

import com.polaris.ai.core.context.CallerContext;

/**
 * 中台控制台用户的 CallerContext 实现。
 */
public class PlatformUserCallerContext implements CallerContext {

    private final Long userId;
    private final Long tenantId;
    private final String username;
    private final boolean tenantAdmin;

    public PlatformUserCallerContext(Long userId, Long tenantId, String username, boolean tenantAdmin) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.tenantAdmin = tenantAdmin;
    }

    @Override
    public Long getUserId() { return userId; }

    @Override
    public String getTenantId() { return tenantId != null ? String.valueOf(tenantId) : null; }

    @Override
    public String getUsername() { return username; }

    @Override
    public Long getDeptId() { return null; } // 中台无部门概念

    @Override
    public boolean isSuperAdmin() { return false; } // 中台用户不是超管

    @Override
    public boolean isPlatformMode() { return true; }
}
