package com.polaris.ai.core.context;

/**
 * 匿名调用者上下文（未登录或公开接口场景）。
 */
public class AnonymousCallerContext implements CallerContext {

    public static final AnonymousCallerContext INSTANCE = new AnonymousCallerContext();

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
        return "anonymous";
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
    public boolean isPlatformMode() {
        return false;
    }
}
