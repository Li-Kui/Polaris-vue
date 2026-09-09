package com.polaris.ai.core.context;

/**
 * 调用者上下文统一静态门面工具类 (CallerUtils)。
 * <p>
 * 为业务层提供零判断、null-safe 的调用者身份提取接口。
 * 自动统一管理后台 (Admin)、中台控制台 (Platform Console)、开放 API Key 及系统异步任务等全场景身份。
 *
 * @author polaris
 */
public final class CallerUtils {

    private CallerUtils() {}

    /**
     * 获取当前调用者上下文（若不存在则返回 AnonymousCallerContext，保证永不为 null）
     */
    public static CallerContext getContext() {
        CallerContext ctx = CallerContextHolder.get();
        return ctx != null ? ctx : AnonymousCallerContext.INSTANCE;
    }

    /**
     * 获取当前用户ID（未登录或API Key时返回 0L）
     */
    public static Long getUserId() {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx != null && ctx.getUserId() != null) {
            return ctx.getUserId();
        }
        return 0L;
    }

    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx != null && ctx.getUsername() != null) {
            return ctx.getUsername();
        }
        return "system";
    }

    /**
     * 获取当前部门ID（管理后台模式下有效，中台模式下为 null）
     */
    public static Long getDeptId() {
        CallerContext ctx = CallerContextHolder.get();
        return ctx != null ? ctx.getDeptId() : null;
    }

    /**
     * 获取当前租户ID（中台模式下有效，管理后台模式下为 null）
     */
    public static String getTenantId() {
        CallerContext ctx = CallerContextHolder.get();
        return ctx != null ? ctx.getTenantId() : null;
    }

    /**
     * 是否为超级管理员（超管拥有跨租户/跨部门查看全部数据的最高权限）
     */
    public static boolean isSuperAdmin() {
        CallerContext ctx = CallerContextHolder.get();
        return ctx != null && ctx.isSuperAdmin();
    }

    /**
     * 是否为中台模式
     */
    public static boolean isPlatformMode() {
        CallerContext ctx = CallerContextHolder.get();
        return ctx != null && ctx.isPlatformMode();
    }
}
