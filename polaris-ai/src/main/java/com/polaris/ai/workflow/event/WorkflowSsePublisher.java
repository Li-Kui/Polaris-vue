package com.polaris.ai.workflow.event;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 工具执行过程中的 JSON SSE 事件发送器。 */
@Component
public class WorkflowSsePublisher {

    private final ConcurrentHashMap<String, AtomicLong> sequenceCursors =
            new ConcurrentHashMap<>();

    public void send(SseEmitter emitter, String executionId, String event, String nodeId, Object payload) {
        synchronized (emitter) {
            long sequence = nextSequence(executionId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("executionId", executionId);
            body.put("sequence", sequence);
            body.put("event", event);
            if (nodeId != null) {
                body.put("nodeId", nodeId);
            }
            body.put("payload", payload == null ? Map.of() : payload);
            try {
                emitter.send(SseEmitter.event()
                        .id(executionId + ":" + sequence)
                        .name(event)
                        .data(body));
            } catch (IOException | IllegalStateException e) {
                throw new WorkflowStreamClosedException(e);
            }
        }
    }

    public void release(String executionId) {
        sequenceCursors.remove(executionId);
    }

    private long nextSequence(String executionId) {
        return sequenceCursors.computeIfAbsent(executionId, key -> new AtomicLong())
                .incrementAndGet();
    }

    public static class WorkflowStreamClosedException extends RuntimeException {
        public WorkflowStreamClosedException(Throwable cause) {
            super("SSE_CONNECTION_LOST", cause);
        }
    }
}
