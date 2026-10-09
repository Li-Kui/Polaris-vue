package com.polaris.platform.auth;

import com.polaris.ai.core.context.WorkflowShareVisitorContext;

/**
 * 分享调用方的 CallerContext 实现。
 */
public class ShareCallerContext implements WorkflowShareVisitorContext {

    private final Long tenantId;
    private final Long shareId;
    private final String workflowCode;
    private final String visitorSessionHash;

    public ShareCallerContext(Long tenantId, Long shareId, String workflowCode) {
        this(tenantId, shareId, workflowCode, null);
    }

    public ShareCallerContext(Long tenantId, Long shareId, String workflowCode, String visitorSessionHash) {
        this.tenantId = tenantId;
        this.shareId = shareId;
        this.workflowCode = workflowCode;
        this.visitorSessionHash = visitorSessionHash;
    }

    @Override
    public String getVisitorSessionHash() { return visitorSessionHash; }

    @Override
    public Long getUserId() { return null; }

    @Override
    public String getTenantId() { return tenantId != null ? String.valueOf(tenantId) : null; }

    @Override
    public String getUsername() {
        return "share:" + shareId;
    }

    @Override
    public Long getDeptId() { return null; }

    @Override
    public boolean isSuperAdmin() { return false; }

    @Override
    public boolean isPlatformMode() { return true; }

    public String getWorkflowCode() {
        return workflowCode;
    }

    public Long getShareId() {
        return shareId;
    }

    @Override
    public boolean hasPermission(String perm) {
        return "workflow:execute".equals(perm) || "workflow:read".equals(perm);
    }
}
