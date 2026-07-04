package com.polaris.ai.utils;

/**
 * 线程安全的联网搜索 Key 持有器，用于在异步大模型回调中传递各模型专属的 Tavily Key
 * 
 * @author polaris
 */
public class SearchKeyHolder {
    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    public static void set(String key) {
        HOLDER.set(key);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
