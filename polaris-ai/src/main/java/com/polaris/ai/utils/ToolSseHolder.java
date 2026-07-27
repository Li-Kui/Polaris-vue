package com.polaris.ai.utils;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 工具执行线程中的 SSE 发射器上下文，用于普通聊天工具向前端推送执行状态。
 */
public class ToolSseHolder {
    private static final ThreadLocal<SseEmitter> HOLDER = new ThreadLocal<>();

    public static void set(SseEmitter emitter) {
        HOLDER.set(emitter);
    }

    public static SseEmitter get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
