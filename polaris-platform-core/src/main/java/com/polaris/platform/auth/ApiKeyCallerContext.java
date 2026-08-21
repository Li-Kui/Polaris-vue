package com.polaris.platform.auth;

import com.polaris.ai.core.context.CallerContext;

/**
 * API Key 调用方的 CallerContext 实现。
 */
public class ApiKeyCallerContext implements CallerContext {

    private final Long tenantId;
    private final String keyName;
    private final String permissions;

    public ApiKeyCallerContext(Long tenantId, String keyName, String permissions) {
        this.tenantId = tenantId;
        this.keyName = keyName;
        this.permissions = permissions;
    }

    @Override
    public Long getUserId() { return null; }

    @Override
    public String getTenantId() { return String.valueOf(tenantId); }

    @Override
    public String getUsername() { return "apikey:" + keyName; }

    @Override
    public Long getDeptId() { return null; }

    @Override
    public boolean isSuperAdmin() { return false; }

    public String getPermissions() { return permissions; }

    public boolean hasPermission(String perm) {
        return permissions != null && permissions.contains(perm);
    }
}
