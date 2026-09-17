package com.polaris.ai.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 统一流式 SSE（Server-Sent Events）推送工具组件
 * 
 * @author polaris
 */
@Component
public class SsePushHelper {

    private static final Logger log = LoggerFactory.getLogger(SsePushHelper.class);

    /**
     * 向前端安全推送一条 SSE 事件
     * 捕获异常防止因客户端断开连接而导致整个线程崩溃
     */
    public boolean sendSse(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
            return true;
        } catch (Exception e) {
            log.warn(">>> SSE 推送失败，客户端可能已断开连接: event={}", event);
            return false;
        }
    }
}
