package com.polaris.ai.context;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.common.core.domain.model.LoginUser;

/**
 * 管理后台模式的 CallerContext 实现。
 * 从 Spring Security 的 LoginUser 中提取调用者信息。
 */
public class SecurityCallerContext implements CallerContext {

    private final Long userId;
    private final String username;
    private final Long deptId;
    private final boolean admin;

    public SecurityCallerContext(LoginUser loginUser) {
        this.userId = loginUser.getUserId();
        this.username = loginUser.getUsername();
        this.deptId = (loginUser.getUser() != null) ? loginUser.getUser().getDeptId() : null;
        this.admin = loginUser.getUserId() != null && loginUser.getUserId() == 1L;
    }

    @Override
    public Long getUserId() { return userId; }

    @Override
    public String getTenantId() { return null; } // 管理后台无租户

    @Override
    public String getUsername() { return username; }

    @Override
    public Long getDeptId() { return deptId; }

    @Override
    public boolean isSuperAdmin() { return admin; }
}
