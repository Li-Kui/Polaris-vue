package com.polaris.ai.workflow.runtime;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.workflow.application.WorkflowExecutionApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowExecutionEventAvailable;
import com.polaris.ai.workflow.application.WorkflowExecutionEventView;
import com.polaris.ai.workflow.application.WorkflowExecutionView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事务事件即时唤醒、数据库事件可重放的 SSE 观察通道。
 * 同一执行的所有浏览器连接共享一次增量查询，周期任务只承担心跳和低频自愈。
 */
@Component
@Slf4j
public class WorkflowEventStreamService {

    private static final int MAX_CONNECTIONS = 500;
    private static final int EVENT_BATCH_SIZE = 500;
    private static final int MAX_DRAIN_ROUNDS = 20;
    private static final long EMITTER_TIMEOUT_MS = 0L;
    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("SUCCEEDED", "FAILED", "CANCELLED", "REJECTED");

    private final WorkflowExecutionApplicationFacade workflowFacade;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> executionConnections = new ConcurrentHashMap<>();
    private final Set<String> requestedExecutions = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean dispatcherRunning = new AtomicBoolean(false);

    public WorkflowEventStreamService(
            WorkflowExecutionApplicationFacade workflowFacade,
            @Qualifier("workflowTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.workflowFacade = workflowFacade;
        this.taskExecutor = taskExecutor;
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
                new AtomicLong(Math.max(0, afterSequence)),
                new AtomicLong(System.currentTimeMillis()));
        connections.put(connectionId, connection);
        executionConnections.computeIfAbsent(executionId,
                ignored -> ConcurrentHashMap.newKeySet()).add(connectionId);
        Runnable cleanup = () -> removeConnection(connectionId);
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
            connection.lastWriteTime().set(System.currentTimeMillis());
            requestDispatch(executionId);
        } catch (IOException e) {
            removeConnection(connectionId);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventAvailable(WorkflowExecutionEventAvailable event) {
        if (executionConnections.containsKey(event.executionId())) {
            requestDispatch(event.executionId());
        }
    }

    /** 心跳不访问数据库。 */
    @Scheduled(fixedDelayString = "${ai.workflow.event-stream-heartbeat-ms:15000}")
    public void maintainConnections() {
        if (connections.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Connection connection : new ArrayList<>(connections.values())) {
            if (now - connection.lastWriteTime().get() >= HEARTBEAT_INTERVAL_MS) {
                try {
                    connection.emitter().send(SseEmitter.event().comment("heartbeat"));
                    connection.lastWriteTime().set(now);
                } catch (Exception e) {
                    removeConnection(connection.connectionId());
                }
            }
        }
    }

    /** 按执行合并的低频安全核对，防止线程拒绝或本地信号遗漏。 */
    @Scheduled(
            fixedDelayString = "${ai.workflow.event-stream-reconcile-ms:60000}",
            initialDelayString = "${ai.workflow.event-stream-reconcile-ms:60000}")
    public void reconcileConnections() {
        executionConnections.keySet().forEach(this::requestDispatch);
    }

    private void requestDispatch(String executionId) {
        if (executionId == null || !executionConnections.containsKey(executionId)) {
            return;
        }
        requestedExecutions.add(executionId);
        submitDispatcher();
    }

    private void submitDispatcher() {
        if (!dispatcherRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            taskExecutor.execute(this::drainRequestedExecutions);
        } catch (RuntimeException e) {
            dispatcherRunning.set(false);
            log.warn("Unable to dispatch workflow SSE events; safety reconcile will retry", e);
        }
    }

    private void drainRequestedExecutions() {
        try {
            while (!requestedExecutions.isEmpty()) {
                List<String> batch = new ArrayList<>(requestedExecutions);
                requestedExecutions.removeAll(batch);
                batch.forEach(this::flushExecutionSafely);
            }
        } finally {
            dispatcherRunning.set(false);
            if (!requestedExecutions.isEmpty()) {
                submitDispatcher();
            }
        }
    }

    private void flushExecutionSafely(String executionId) {
        List<Connection> targets = connectionsFor(executionId);
        if (targets.isEmpty()) {
            return;
        }
        CallerContext previous = CallerContextHolder.get();
        try {
            CallerContextHolder.set(targets.get(0).caller());
            for (int round = 0; round < MAX_DRAIN_ROUNDS; round++) {
                targets = connectionsFor(executionId);
                if (targets.isEmpty()) {
                    return;
                }
                long afterSequence = targets.stream()
                        .mapToLong(connection -> connection.sequence().get())
                        .min().orElse(0L);
                List<WorkflowExecutionEventView> events = workflowFacade.listEvents(
                        executionId, afterSequence, EVENT_BATCH_SIZE);
                sendEvents(targets, events);
                if (events.size() < EVENT_BATCH_SIZE) {
                    completeIfTerminal(executionId);
                    return;
                }
            }
            requestedExecutions.add(executionId);
        } catch (Exception e) {
            connectionsFor(executionId).forEach(connection -> {
                removeConnection(connection.connectionId());
                connection.emitter().completeWithError(e);
            });
        } finally {
            CallerContextHolder.clear();
            if (previous != null) {
                CallerContextHolder.set(previous);
            }
        }
    }

    private void sendEvents(
            List<Connection> targets,
            List<WorkflowExecutionEventView> events) {
        for (Connection connection : targets) {
            try {
                for (WorkflowExecutionEventView event : events) {
                    if (event.sequenceNo() <= connection.sequence().get()) {
                        continue;
                    }
                    connection.emitter().send(SseEmitter.event()
                            .id(String.valueOf(event.sequenceNo()))
                            .name("workflow")
                            .data(event));
                    connection.sequence().set(event.sequenceNo());
                    connection.lastWriteTime().set(System.currentTimeMillis());
                }
            } catch (Exception e) {
                removeConnection(connection.connectionId());
                connection.emitter().completeWithError(e);
            }
        }
    }

    private void completeIfTerminal(String executionId) {
        List<Connection> targets = connectionsFor(executionId);
        if (targets.isEmpty()) {
            return;
        }
        WorkflowExecutionView execution = workflowFacade.get(executionId);
        if (!TERMINAL_STATUSES.contains(execution.status())) {
            return;
        }
        for (Connection connection : targets) {
            if (connection.sequence().get() >= execution.eventSequence()) {
                removeConnection(connection.connectionId());
                connection.emitter().complete();
            }
        }
    }

    private List<Connection> connectionsFor(String executionId) {
        Set<String> ids = executionConnections.get(executionId);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().map(connections::get).filter(Objects::nonNull).toList();
    }

    private void removeConnection(String connectionId) {
        Connection removed = connections.remove(connectionId);
        if (removed == null) {
            return;
        }
        executionConnections.computeIfPresent(removed.executionId(), (executionId, ids) -> {
            ids.remove(connectionId);
            return ids.isEmpty() ? null : ids;
        });
    }

    private record Connection(
            String connectionId,
            String executionId,
            CallerContext caller,
            SseEmitter emitter,
            AtomicLong sequence,
            AtomicLong lastWriteTime) {
    }
}
