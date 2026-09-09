package com.polaris.ai.workflow.runtime;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.workflow.application.WorkflowExecutionApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowExecutionEventView;
import com.polaris.ai.workflow.application.WorkflowExecutionView;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 基于数据库的 SSE 观察通道；工作流执行不依赖实时连接。 */
@Component
public class WorkflowEventStreamService {

    private static final int MAX_CONNECTIONS = 500;
    private static final long EMITTER_TIMEOUT_MS = 300_000L;
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("SUCCEEDED", "FAILED", "CANCELLED", "REJECTED");

    private final WorkflowExecutionApplicationFacade workflowFacade;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public WorkflowEventStreamService(WorkflowExecutionApplicationFacade workflowFacade) {
        this.workflowFacade = workflowFacade;
    }

    public SseEmitter subscribe(String executionId, long afterSequence) {
        CallerContext caller = CallerContextHolder.require();
        WorkflowExecutionView execution = workflowFacade.get(executionId);
        if (connections.size() >= MAX_CONNECTIONS) {
            throw new IllegalStateException("工作流事件连接数已达上限");
        }
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Connection connection = new Connection(
                connectionId, executionId, caller, emitter,
                new AtomicLong(Math.max(0, afterSequence)), System.currentTimeMillis());
        connections.put(connectionId, connection);
        Runnable cleanup = () -> connections.remove(connectionId);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "executionId", executionId,
                            "afterSequence", connection.sequence().get(),
                            "status", execution.status())));
        } catch (IOException e) {
            cleanup.run();
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @Scheduled(fixedDelayString = "${ai.workflow.event-stream-poll-ms:5000}")
    public void pollConnections() {
        if (connections.isEmpty()) {
            return;
        }
        for (Connection connection : connections.values()) {
            poll(connection);
        }
    }

    private void poll(Connection connection) {
        try {
            CallerContextHolder.set(connection.caller());
            List<WorkflowExecutionEventView> events = workflowFacade.listEvents(
                    connection.executionId(), connection.sequence().get(), 200);
            for (WorkflowExecutionEventView event : events) {
                connection.emitter().send(SseEmitter.event()
                        .id(String.valueOf(event.sequenceNo()))
                        .name("workflow")
                        .data(event));
                connection.sequence().set(event.sequenceNo());
                connection.lastWriteTime().set(System.currentTimeMillis());
            }
            WorkflowExecutionView execution = workflowFacade.get(connection.executionId());
            if (TERMINAL_STATUSES.contains(execution.status())
                    && connection.sequence().get() >= execution.eventSequence()) {
                connections.remove(connection.connectionId());
                connection.emitter().complete();
            } else if (events.isEmpty()
                    && System.currentTimeMillis() - connection.lastWriteTime().get() >= 15_000L) {
                connection.emitter().send(SseEmitter.event().comment("heartbeat"));
                connection.lastWriteTime().set(System.currentTimeMillis());
            }
        } catch (Exception e) {
            connections.remove(connection.connectionId());
            connection.emitter().completeWithError(e);
        } finally {
            CallerContextHolder.clear();
        }
    }

    private record Connection(
            String connectionId,
            String executionId,
            CallerContext caller,
            SseEmitter emitter,
            AtomicLong sequence,
            AtomicLong lastWriteTime) {

        Connection(
                String connectionId,
                String executionId,
                CallerContext caller,
                SseEmitter emitter,
                AtomicLong sequence,
                long lastWriteTime) {
            this(connectionId, executionId, caller, emitter,
                    sequence, new AtomicLong(lastWriteTime));
        }
    }
}
