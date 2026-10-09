package com.polaris.ai.workflow.runtime;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 工作流事件必须逐条透传，不能被反向代理缓存至连接结束。 */
final class WorkflowSseEmitter extends SseEmitter {

    WorkflowSseEmitter(long timeout) {
        super(timeout);
    }

    @Override
    protected void extendResponse(ServerHttpResponse response) {
        super.extendResponse(response);
        response.getHeaders().set("X-Accel-Buffering", "no");
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "private, no-store, no-transform");
    }
}
