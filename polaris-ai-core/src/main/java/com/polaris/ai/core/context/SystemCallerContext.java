package com.polaris.ai.core.context;

/**
 * 系统内部调用上下文（定时任务、系统初始化等场景）。
 */
public class SystemCallerContext implements CallerContext {

    public static final SystemCallerContext INSTANCE = new SystemCallerContext();

    @Override
    public Long getUserId() {
        return 0L;
    }

    @Override
    public String getTenantId() {
        return null;
    }

    @Override
    public String getUsername() {
        return "system";
    }

    @Override
    public Long getDeptId() {
        return null;
    }

    @Override
    public boolean isSuperAdmin() {
        return true;
    }
}
