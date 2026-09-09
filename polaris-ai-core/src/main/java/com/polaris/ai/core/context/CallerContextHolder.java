package com.polaris.ai.core.context;

/**
 * 基于 ThreadLocal 的 CallerContext 持有者。
 * 由鉴权过滤器在请求开始时填充，请求结束时清除。
 */
public final class CallerContextHolder {

    private static final ThreadLocal<CallerContext> HOLDER = new ThreadLocal<>();

    private CallerContextHolder() {}

    public static void set(CallerContext context) {
        if (context == null) {
            clear();
        } else {
            HOLDER.set(context);
        }
    }

    public static CallerContext get() {
        return HOLDER.get();
    }

    public static CallerContext require() {
        CallerContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new IllegalStateException("当前线程未设置 CallerContext");
        }
        return ctx;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
