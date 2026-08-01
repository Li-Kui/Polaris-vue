package com.polaris.ai.workflow.event;

import com.polaris.ai.workflow.runtime.WorkflowExecutionStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** V2.1 JSON SSE 事件的唯一发送入口。 */
@Component
public class WorkflowSsePublisher {

    private final WorkflowExecutionStore executionStore;
    private final int sequenceBlockSize;
    private final ConcurrentHashMap<String, SequenceCursor> sequenceCursors =
            new ConcurrentHashMap<>();

    public WorkflowSsePublisher(
            WorkflowExecutionStore executionStore,
            @Value("${ai.workflow.event-sequence-block-size:64}") int sequenceBlockSize) {
        this.executionStore = executionStore;
        this.sequenceBlockSize = Math.max(1, Math.min(4096, sequenceBlockSize));
    }

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
        AtomicLong allocated = new AtomicLong();
        sequenceCursors.compute(executionId, (key, cursor) -> {
            SequenceCursor active = cursor;
            if (active == null || active.next() > active.last()) {
                WorkflowExecutionStore.EventSequenceBlock block =
                        executionStore.reserveEventSequences(executionId, sequenceBlockSize);
                active = new SequenceCursor(block.first(), block.last());
            }
            allocated.set(active.next());
            return new SequenceCursor(active.next() + 1, active.last());
        });
        return allocated.get();
    }

    private record SequenceCursor(long next, long last) {
    }

    public static class WorkflowStreamClosedException extends RuntimeException {
        public WorkflowStreamClosedException(Throwable cause) {
            super("SSE_CONNECTION_LOST", cause);
        }
    }
}
