package com.polaris.platform.auth;

import com.alibaba.fastjson2.JSON;
import com.polaris.ai.core.context.CallerContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * API Key 调用方的 CallerContext 实现。
 */
public class ApiKeyCallerContext implements CallerContext {

    private final Long tenantId;
    private final Long apiKeyId;
    private final String keyName;
    private final String permissions;
    private final Set<String> permissionSet;

    public ApiKeyCallerContext(Long tenantId, String keyName, String permissions) {
        this(tenantId, null, keyName, permissions);
    }

    public ApiKeyCallerContext(
            Long tenantId, Long apiKeyId, String keyName, String permissions) {
        this.tenantId = tenantId;
        this.apiKeyId = apiKeyId;
        this.keyName = keyName;
        this.permissions = permissions;
        this.permissionSet = parsePermissions(permissions);
    }

    @Override
    public Long getUserId() { return null; }

    @Override
    public String getTenantId() { return tenantId != null ? String.valueOf(tenantId) : null; }

    @Override
    public String getUsername() {
        return "apikey:" + (apiKeyId == null ? keyName : apiKeyId);
    }

    @Override
    public Long getDeptId() { return null; }

    @Override
    public boolean isSuperAdmin() { return false; }

    @Override
    public boolean isPlatformMode() { return true; }

    public String getPermissions() { return permissions; }

    @Override
    public boolean hasPermission(String perm) {
        return perm != null && !perm.isBlank() && permissionSet.contains(perm);
    }

    private Set<String> parsePermissions(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptySet();
        }
        try {
            Set<String> result = new HashSet<>(JSON.parseArray(value, String.class));
            result.removeIf(item -> item == null || item.isBlank());
            return Collections.unmodifiableSet(result);
        } catch (Exception ignored) {
            Set<String> result = new HashSet<>();
            Arrays.stream(value.split("[,\\s]+"))
                    .map(item -> item.replaceAll("^[\\[\\\"]+|[\\]\\\"]+$", ""))
                    .filter(item -> !item.isBlank())
                    .forEach(result::add);
            return Collections.unmodifiableSet(result);
        }
    }
}
