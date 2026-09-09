package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.application.WorkflowTaskSignal;
import com.polaris.ai.workflow.application.WorkflowTimerSignal;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.domain.WorkflowExecution;
import com.polaris.ai.workflow.mapper.WorkflowExecutionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 精准唤醒最近的持久化任务，并通过低频轮询完成宕机恢复。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.workflow", name = "enabled", havingValue = "true")
public class WorkflowWorker {

    private static final long BATCH_DRAIN_DELAY_MS = 25L;
    private static final long DUE_RETRY_BASE_MS = 250L;
    private static final long DUE_RETRY_MAX_MS = 30000L;

    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowExecutionEngine executionEngine;
    private final WorkflowProperties properties;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ScheduledExecutorService timerExecutor;
    private final String runnerId;
    private final AtomicBoolean signalPending = new AtomicBoolean(false);
    private final Object timerLock = new Object();
    private Instant nextWakeAt;
    private ScheduledFuture<?> nextWakeTask;
    private long timerRevision;
    private int dueWithoutProgress;

    public WorkflowWorker(
            WorkflowExecutionMapper executionMapper,
            WorkflowExecutionEngine executionEngine,
            WorkflowProperties properties,
            @Qualifier("workflowTaskExecutor") ThreadPoolTaskExecutor taskExecutor,
            @Qualifier("scheduledExecutorService") ScheduledExecutorService timerExecutor) {
        this.executionMapper = executionMapper;
        this.executionEngine = executionEngine;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
        this.timerExecutor = timerExecutor;
        if (timerExecutor instanceof ScheduledThreadPoolExecutor scheduledExecutor) {
            scheduledExecutor.setRemoveOnCancelPolicy(true);
        }
        this.runnerId = ManagementFactory.getRuntimeMXBean().getName()
                + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Scheduled(
            fixedDelayString = "${ai.workflow.worker-poll-ms:30000}",
            initialDelayString = "${ai.workflow.worker-initial-delay-ms:1000}")
    public void poll() {
        signalPending.set(false);
        if (!properties.isEnabled() || !properties.isWorkerEnabled()) {
            return;
        }
        int claimedCount = 0;
        try {
            for (String executionId : executionMapper.selectClaimCandidates(
                    properties.getWorkerBatchSize())) {
                if (executionMapper.claimLease(
                        executionId, runnerId, properties.getLeaseSeconds()) != 1) {
                    continue;
                }
                claimedCount++;
                WorkflowExecution claimed = executionMapper.selectByExecutionId(executionId);
                if (claimed == null || claimed.getFencingToken() == null) {
                    continue;
                }
                try {
                    long fencingToken = claimed.getFencingToken();
                    taskExecutor.execute(() -> executionEngine.execute(
                            executionId, runnerId, fencingToken, properties.getLeaseSeconds()));
                } catch (RuntimeException e) {
                    log.warn("Workflow worker queue rejected execution {}", executionId, e);
                }
            }
        } finally {
            schedulePersistedWakeup(claimedCount > 0);
        }
    }

    /** 事务提交后异步唤醒，带防抖。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskSignal(WorkflowTaskSignal signal) {
        requestPoll();
    }

    /** 新的等待时间提交后，只在它早于当前闹钟时重新调度。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTimerSignal(WorkflowTimerSignal signal) {
        if (signal.refresh()) {
            synchronized (timerLock) {
                timerRevision++;
            }
            try {
                timerExecutor.execute(() -> schedulePersistedWakeup(false));
            } catch (RuntimeException e) {
                log.warn("Workflow timer refresh rejected", e);
            }
        } else if (signal.resumeAt() != null) {
            registerTimer(signal.resumeAt());
        }
    }

    private void requestPoll() {
        if (signalPending.compareAndSet(false, true)) {
            try {
                taskExecutor.execute(this::poll);
            } catch (RuntimeException e) {
                signalPending.set(false);
                log.warn("Workflow worker wakeup rejected", e);
            }
        }
    }

    private void schedulePersistedWakeup(boolean madeProgress) {
        long observedRevision;
        synchronized (timerLock) {
            observedRevision = timerRevision;
        }
        Date resumeTime;
        try {
            resumeTime = executionMapper.selectNextResumeTime();
        } catch (RuntimeException e) {
            log.warn("Unable to schedule next workflow timer wakeup", e);
            return;
        }
        synchronized (timerLock) {
            if (observedRevision != timerRevision) {
                return;
            }
            if (resumeTime == null) {
                dueWithoutProgress = 0;
                cancelScheduledWakeupLocked();
                return;
            }
            Instant wakeAt = resumeTime.toInstant();
            Instant now = Instant.now();
            if (!wakeAt.isAfter(now)) {
                if (madeProgress) {
                    dueWithoutProgress = 0;
                    wakeAt = now.plusMillis(BATCH_DRAIN_DELAY_MS);
                } else {
                    wakeAt = now.plusMillis(nextDueRetryDelayLocked());
                }
            } else {
                dueWithoutProgress = 0;
            }
            reconcileScheduledWakeupLocked(wakeAt);
        }
    }

    private void registerTimer(Instant wakeAt) {
        if (!properties.isEnabled() || !properties.isWorkerEnabled()) {
            return;
        }
        synchronized (timerLock) {
            timerRevision++;
            if (nextWakeAt == null || wakeAt.isBefore(nextWakeAt)) {
                replaceScheduledWakeupLocked(wakeAt);
            }
        }
    }

    private long nextDueRetryDelayLocked() {
        int exponent = Math.min(dueWithoutProgress, 7);
        dueWithoutProgress = Math.min(dueWithoutProgress + 1, 8);
        return Math.min(DUE_RETRY_MAX_MS, DUE_RETRY_BASE_MS << exponent);
    }

    private void reconcileScheduledWakeupLocked(Instant wakeAt) {
        if (nextWakeAt != null
                && Math.abs(nextWakeAt.toEpochMilli() - wakeAt.toEpochMilli()) <= 1L) {
            return;
        }
        replaceScheduledWakeupLocked(wakeAt);
    }

    private void replaceScheduledWakeupLocked(Instant wakeAt) {
        cancelScheduledWakeupLocked();
        nextWakeAt = wakeAt;
        long delayMillis = delayMillisUntil(Instant.now(), wakeAt);
        try {
            nextWakeTask = timerExecutor.schedule(
                    () -> fireTimer(wakeAt), delayMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            nextWakeAt = null;
            nextWakeTask = null;
            log.warn("Workflow timer wakeup rejected", e);
        }
    }

    static long delayMillisUntil(Instant now, Instant wakeAt) {
        Duration delay = Duration.between(now, wakeAt);
        if (delay.isNegative() || delay.isZero()) {
            return 0L;
        }
        long millis = delay.toMillis();
        return delay.getNano() % 1_000_000 == 0 ? millis : millis + 1L;
    }

    private void cancelScheduledWakeupLocked() {
        if (nextWakeTask != null) {
            nextWakeTask.cancel(false);
        }
        nextWakeAt = null;
        nextWakeTask = null;
    }

    private void fireTimer(Instant wakeAt) {
        synchronized (timerLock) {
            if (!wakeAt.equals(nextWakeAt)) {
                return;
            }
            nextWakeAt = null;
            nextWakeTask = null;
            timerRevision++;
        }
        requestPoll();
    }
}
