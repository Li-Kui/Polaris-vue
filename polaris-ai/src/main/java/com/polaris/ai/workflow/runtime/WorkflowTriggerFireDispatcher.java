package com.polaris.ai.workflow.runtime;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.core.context.SystemCallerContext;
import com.polaris.ai.core.context.TenantSystemCallerContext;
import com.polaris.ai.workflow.application.WorkflowExecutionView;
import com.polaris.ai.workflow.application.WorkflowTriggerApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowTriggerFirePending;
import com.polaris.ai.workflow.application.WorkflowTriggerInvocationCommand;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.domain.WorkflowTrigger;
import com.polaris.ai.workflow.domain.WorkflowTriggerFire;
import com.polaris.ai.workflow.mapper.WorkflowTriggerFireMapper;
import com.polaris.ai.workflow.mapper.WorkflowTriggerMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.lang.management.ManagementFactory;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 从可靠批次表异步创建工作流执行，失败时按租约和退避策略重试。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.workflow", name = "enabled", havingValue = "true")
@ConditionalOnProperty(
        prefix = "ai.workflow", name = "trigger-scheduler-mode",
        havingValue = "quartz", matchIfMissing = true)
public class WorkflowTriggerFireDispatcher {

    private static final int MAX_DRAIN_ROUNDS = 10;

    private final WorkflowTriggerFireMapper fireMapper;
    private final WorkflowTriggerMapper triggerMapper;
    private final WorkflowTriggerApplicationFacade triggerFacade;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ScheduledExecutorService timerExecutor;
    private final String publisherId;
    private final AtomicBoolean requested = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    public WorkflowTriggerFireDispatcher(
            WorkflowTriggerFireMapper fireMapper,
            WorkflowTriggerMapper triggerMapper,
            WorkflowTriggerApplicationFacade triggerFacade,
            WorkflowProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("workflowTaskExecutor") ThreadPoolTaskExecutor taskExecutor,
            @Qualifier("scheduledExecutorService") ScheduledExecutorService timerExecutor) {
        this.fireMapper = fireMapper;
        this.triggerMapper = triggerMapper;
        this.triggerFacade = triggerFacade;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.timerExecutor = timerExecutor;
        this.publisherId = ManagementFactory.getRuntimeMXBean().getName()
                + ":trigger-fire:" + UUID.randomUUID().toString().substring(0, 8);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAtStartup() {
        requestDispatch();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFirePending(WorkflowTriggerFirePending event) {
        requestDispatch();
    }

    /** 仅用于进程崩溃、线程池拒绝和租约过期后的低频恢复。 */
    @Scheduled(
            fixedDelayString = "${ai.workflow.trigger-fire-recovery-poll-ms:600000}",
            initialDelayString = "${ai.workflow.trigger-fire-recovery-initial-delay-ms:600000}")
    public void recover() {
        requestDispatch();
    }

    void requestDispatch() {
        requested.set(true);
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            taskExecutor.execute(this::drain);
        } catch (RuntimeException e) {
            running.set(false);
            log.warn("Workflow trigger fire dispatch rejected", e);
        }
    }

    private void drain() {
        try {
            do {
                requested.set(false);
                drainAvailable();
            } while (requested.get());
        } catch (RuntimeException e) {
            log.error("Unable to drain workflow trigger fires", e);
        } finally {
            running.set(false);
            if (requested.get()) {
                requestDispatch();
            }
        }
    }

    private void drainAvailable() {
        if (!properties.isEnabled() || !properties.isAcceptingNewExecutions()) {
            return;
        }
        fireMapper.markExpiredExhausted(properties.getTriggerFireMaxAttempts());
        int batchSize = properties.getTriggerFireBatchSize();
        for (int round = 0; round < MAX_DRAIN_ROUNDS; round++) {
            var candidates = fireMapper.selectDispatchCandidates(
                    batchSize, properties.getTriggerFireMaxAttempts());
            if (candidates.isEmpty()) {
                return;
            }
            for (String fireId : candidates) {
                dispatchOne(fireId);
            }
            if (candidates.size() < batchSize) {
                return;
            }
        }
        requested.set(true);
    }

    private void dispatchOne(String fireId) {
        if (fireMapper.claim(fireId, publisherId, properties.getTriggerFireLeaseSeconds(),
                properties.getTriggerFireMaxAttempts()) != 1) {
            return;
        }
        WorkflowTriggerFire fire = fireMapper.selectByFireId(fireId);
        if (fire == null || !publisherId.equals(fire.getClaimedBy())) {
            return;
        }
        WorkflowTrigger trigger = triggerMapper.selectByTriggerIdInternal(fire.getTriggerId());
        if (trigger == null || !"ACTIVE".equals(trigger.getStatus())) {
            fireMapper.markCancelled(fireId, publisherId, "触发器已停用或删除");
            return;
        }

        CallerContext previous = CallerContextHolder.get();
        CallerContextHolder.set(fire.getTenantId() == null
                ? SystemCallerContext.INSTANCE
                : new TenantSystemCallerContext(fire.getTenantId()));
        try {
            String idempotencyKey = "schedule:" + fire.getScheduledTime().getTime();
            WorkflowExecutionView execution = triggerFacade.invoke(
                    fire.getTriggerId(), new WorkflowTriggerInvocationCommand(
                            objectMapper.createObjectNode(), idempotencyKey));
            if (fireMapper.markDispatched(fireId, publisherId, execution.executionId()) == 1) {
                triggerMapper.recordScheduledTriggerResult(
                        fire.getTriggerId(), fire.getScheduledTime(), execution.executionId(),
                        "DISPATCHED", null);
            }
        } catch (RuntimeException e) {
            markFailure(fire, e);
        } finally {
            CallerContextHolder.clear();
            if (previous != null) {
                CallerContextHolder.set(previous);
            }
        }
    }

    private void markFailure(WorkflowTriggerFire fire, RuntimeException failure) {
        WorkflowTrigger current = triggerMapper.selectByTriggerIdInternal(fire.getTriggerId());
        if (current == null || !"ACTIVE".equals(current.getStatus())) {
            fireMapper.markCancelled(fire.getFireId(), publisherId, "触发器已停用或删除");
            return;
        }
        int attempt = fire.getAttemptCount() == null ? 1 : fire.getAttemptCount();
        boolean dead = attempt >= properties.getTriggerFireMaxAttempts();
        int delaySeconds = Math.min(300, 1 << Math.min(8, Math.max(1, attempt)));
        String message = safeMessage(failure);
        if (fireMapper.markFailed(fire.getFireId(), publisherId,
                dead ? "DEAD" : "FAILED", delaySeconds, message) == 1) {
            triggerMapper.recordScheduledTriggerResult(
                    fire.getTriggerId(), fire.getScheduledTime(), null,
                    dead ? "DEAD" : "FAILED", message);
            if (!dead) {
                timerExecutor.schedule(this::requestDispatch, delaySeconds, TimeUnit.SECONDS);
            }
        }
        log.warn("Workflow trigger fire dispatch failed: fireId={}, attempt={}",
                fire.getFireId(), attempt, failure);
    }

    private String safeMessage(RuntimeException failure) {
        return "触发执行失败（" + failure.getClass().getSimpleName() + "）";
    }
}
