package com.polaris.ai.utils;

/**
 * 跨线程传递当前请求的 BaseUrl 域名与端口的持有者
 * 
 * @author polaris
 */
public class BaseUrlHolder {
    
    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    public static void set(String baseUrl) {
        HOLDER.set(baseUrl);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
