package com.polaris.ai.workflow.runtime;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.core.context.WorkflowScopedCallerContext;
import com.polaris.ai.workflow.application.*;
import com.polaris.ai.workflow.spi.WorkflowPrincipalContextProvider;
import com.polaris.common.exception.ServiceException;
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
public class WorkflowEventStreamService implements WorkflowEventStreamApplicationFacade {

    private static final int MAX_CONNECTIONS = 500;
    private static final int EVENT_BATCH_SIZE = 500;
    private static final int MAX_DRAIN_ROUNDS = 20;
    private static final long EMITTER_TIMEOUT_MS = 0L;
    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("SUCCEEDED", "FAILED", "CANCELLED", "REJECTED");

    private final WorkflowExecutionApplicationFacade workflowFacade;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final List<WorkflowPrincipalContextProvider> principalProviders;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> executionConnections = new ConcurrentHashMap<>();
    private final Set<String> requestedExecutions = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean dispatcherRunning = new AtomicBoolean(false);

    public WorkflowEventStreamService(
            WorkflowExecutionApplicationFacade workflowFacade,
            @Qualifier("workflowTaskExecutor") ThreadPoolTaskExecutor taskExecutor,
            List<WorkflowPrincipalContextProvider> principalProviders) {
        this.workflowFacade = workflowFacade;
        this.taskExecutor = taskExecutor;
        this.principalProviders = List.copyOf(principalProviders);
    }

    @Override
    public SseEmitter subscribe(String executionId, long afterSequence) {
        CallerContext caller = CallerContextHolder.require();
        WorkflowExecutionView execution = workflowFacade.get(executionId);
        if (connections.size() >= MAX_CONNECTIONS) {
            throw new IllegalStateException("工作流事件连接数已达上限");
        }
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = new WorkflowSseEmitter(EMITTER_TIMEOUT_MS);
        Connection connection = new Connection(
                connectionId, executionId, execution.workflowCode(), caller, emitter,
                new AtomicLong(Math.max(0, afterSequence)),
                new AtomicLong(System.currentTimeMillis()));
        if (!isAuthorized(connection)) {
            throw new ServiceException("工作流事件访问权限已失效", 403);
        }
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

    /** 心跳只复查连接授权，不查询执行或事件。 */
    @Scheduled(fixedDelayString = "${ai.workflow.event-stream-heartbeat-ms:15000}")
    public void maintainConnections() {
        if (connections.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Connection connection : new ArrayList<>(connections.values())) {
            if (!isAuthorized(connection)) {
                revokeConnection(connection);
                continue;
            }
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
        List<Connection> targets = authorizedConnectionsFor(executionId);
        if (targets.isEmpty()) {
            return;
        }
        CallerContext previous = CallerContextHolder.get();
        try {
            for (int round = 0; round < MAX_DRAIN_ROUNDS; round++) {
                targets = authorizedConnectionsFor(executionId);
                if (targets.isEmpty()) {
                    return;
                }
                CallerContextHolder.set(targets.get(0).caller());
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
            if (!isAuthorized(connection)) {
                revokeConnection(connection);
                continue;
            }
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

    private List<Connection> authorizedConnectionsFor(String executionId) {
        List<Connection> authorized = new ArrayList<>();
        for (Connection connection : connectionsFor(executionId)) {
            if (isAuthorized(connection)) authorized.add(connection);
            else revokeConnection(connection);
        }
        return authorized;
    }

    private boolean isAuthorized(Connection connection) {
        CallerContext caller = connection.caller();
        String username = caller.getUsername();
        String type = username != null && username.startsWith("apikey:") ? "API_KEY"
                : username != null && username.startsWith("share:") ? "SHARE"
                : caller.isPlatformMode() ? "PLATFORM_USER" : null;
        if (type == null) return true;
        try {
            Long tenantId = Long.valueOf(caller.getTenantId());
            String principalId = "API_KEY".equals(type) ? username.substring("apikey:".length())
                    : "SHARE".equals(type) ? username.substring("share:".length())
                    : String.valueOf(caller.getUserId());
            for (WorkflowPrincipalContextProvider provider : principalProviders) {
                if (!provider.supports(type)) continue;
                Optional<CallerContext> current = provider.resolve(tenantId, type, principalId);
                if (current.isEmpty()) return false;
                CallerContext refreshed = current.get();
                if (!Objects.equals(caller.getTenantId(), refreshed.getTenantId())
                        || !Objects.equals(username, refreshed.getUsername())) return false;
                if (("API_KEY".equals(type) || "SHARE".equals(type))
                        && !refreshed.hasPermission("workflow:read")) return false;
                if ("API_KEY".equals(type) && !(refreshed instanceof WorkflowScopedCallerContext)) {
                    return false;
                }
                return !(refreshed instanceof WorkflowScopedCallerContext scoped)
                        || scoped.canAccessWorkflow(connection.workflowCode());
            }
        } catch (RuntimeException e) {
            // 授权查询不可用时关闭旧连接，不沿用订阅时的权限或向客户端暴露内部异常。
            return false;
        }
        return false;
    }

    private void revokeConnection(Connection connection) {
        removeConnection(connection.connectionId());
        connection.emitter().complete();
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
            String workflowCode,
            CallerContext caller,
            SseEmitter emitter,
            AtomicLong sequence,
            AtomicLong lastWriteTime) {
    }
}
