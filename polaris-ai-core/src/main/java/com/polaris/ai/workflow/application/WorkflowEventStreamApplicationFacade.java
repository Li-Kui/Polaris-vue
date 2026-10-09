package com.polaris.ai.workflow.application;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 工作流持久化事件的 SSE 订阅边界。 */
public interface WorkflowEventStreamApplicationFacade {

    SseEmitter subscribe(String executionId, long afterSequence);
}
