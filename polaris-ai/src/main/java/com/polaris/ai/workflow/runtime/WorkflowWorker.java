package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.application.WorkflowTaskSignal;
import com.polaris.ai.workflow.application.WorkflowTimerSignal;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.domain.WorkflowExecution;
import com.polaris.ai.workflow.mapper.WorkflowExecutionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 事务事件负责即时唤醒，数据库中的最近动作负责精准恢复，
 * 递增间隔的空闲核对只承担漏事件和宕机恢复兜底。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.workflow", name = "enabled", havingValue = "true")
public class WorkflowWorker {

    private static final long BATCH_DRAIN_DELAY_MS = 25L;
    private static final long COORDINATOR_RETRY_MS = 250L;
    private static final long DUE_RETRY_BASE_MS = 250L;
    private static final long DUE_RETRY_MAX_MS = 30000L;

    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowExecutionEngine executionEngine;
    private final WorkflowProperties properties;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ScheduledExecutorService timerExecutor;
    private final String runnerId;
    private final AtomicBoolean pollRequested = new AtomicBoolean(false);
    private final AtomicBoolean refreshRequested = new AtomicBoolean(false);
    private final AtomicBoolean coordinatorRunning = new AtomicBoolean(false);
    private final Object timerLock = new Object();
    private Instant nextWakeAt;
    private ScheduledFuture<?> nextWakeTask;
    private boolean pollAtNextWake;
    private long timerRevision;
    private int dueWithoutProgress;
    private long idleReconcileDelayMs;

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
        this.idleReconcileDelayMs = properties.getWorkerIdleReconcileInitialMs();
        if (timerExecutor instanceof ScheduledThreadPoolExecutor scheduledExecutor) {
            scheduledExecutor.setRemoveOnCancelPolicy(true);
        }
        this.runnerId = ManagementFactory.getRuntimeMXBean().getName()
                + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 启动时只查询最近动作；确有到期任务时才执行批量抢占查询。 */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        requestScheduleRefresh(true);
    }

    /** 保留显式唤醒入口，实际轮询由串行协调器执行。 */
    public void poll() {
        requestPoll(true);
    }

    /** 新任务在事务提交后即时唤醒。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskSignal(WorkflowTaskSignal signal) {
        requestPoll(true);
    }

    /** 等待时间变化在事务提交后刷新最近动作，已知时间则直接注册精准闹钟。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTimerSignal(WorkflowTimerSignal signal) {
        if (signal.refresh()) {
            requestScheduleRefresh(true);
        } else if (signal.resumeAt() != null) {
            registerTimer(signal.resumeAt());
        }
    }

    private void requestPoll(boolean resetBackoff) {
        if (!workerEnabled()) {
            return;
        }
        synchronized (timerLock) {
            timerRevision++;
            cancelScheduledWakeupLocked();
            if (resetBackoff) {
                resetBackoffLocked();
            }
        }
        pollRequested.set(true);
        submitCoordinator();
    }

    private void requestScheduleRefresh(boolean resetBackoff) {
        if (!workerEnabled()) {
            return;
        }
        synchronized (timerLock) {
            timerRevision++;
            cancelScheduledWakeupLocked();
            if (resetBackoff) {
                resetBackoffLocked();
            }
        }
        refreshRequested.set(true);
        submitCoordinator();
    }

    /**
     * 同一时刻只运行一个协调器，避免定时唤醒、任务事件和恢复事件
     * 并发扫描同一批数据。
     */
    private void submitCoordinator() {
        if (!coordinatorRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            taskExecutor.execute(this::runCoordinator);
        } catch (RuntimeException e) {
            coordinatorRunning.set(false);
            log.warn("Workflow coordinator wakeup rejected; retrying", e);
            scheduleCoordinatorRetry();
        }
    }

    private void runCoordinator() {
        try {
            while (workerEnabled()) {
                if (pollRequested.getAndSet(false)) {
                    pollOnce();
                    continue;
                }
                if (refreshRequested.getAndSet(false)) {
                    schedulePersistedWakeup(false);
                    continue;
                }
                break;
            }
        } finally {
            coordinatorRunning.set(false);
            if (workerEnabled() && (pollRequested.get() || refreshRequested.get())) {
                submitCoordinator();
            }
        }
    }

    private void pollOnce() {
        int capacity = availableDispatchCapacity();
        if (capacity <= 0) {
            schedulePollAfter(COORDINATOR_RETRY_MS);
            return;
        }

        int limit = Math.min(properties.getWorkerBatchSize(), capacity);
        List<String> candidates;
        try {
            candidates = executionMapper.selectClaimCandidates(limit);
        } catch (RuntimeException e) {
            log.warn("Unable to select workflow claim candidates", e);
            schedulePollAfter(nextDueRetryDelay());
            return;
        }

        int claimedCount = 0;
        for (String executionId : candidates) {
            if (executionMapper.claimLease(
                    executionId, runnerId, properties.getLeaseSeconds()) != 1) {
                continue;
            }
            WorkflowExecution claimed = executionMapper.selectByExecutionId(executionId);
            if (claimed == null || claimed.getFencingToken() == null) {
                log.warn("Workflow execution {} was claimed but cannot be loaded", executionId);
                continue;
            }

            claimedCount++;
            long fencingToken = claimed.getFencingToken();
            Runnable executionTask = () -> executionEngine.execute(
                    executionId, runnerId, fencingToken, properties.getLeaseSeconds());
            try {
                taskExecutor.execute(executionTask);
            } catch (RuntimeException e) {
                // 租约已经成功落库，拒绝后不能把任务遗留到租约过期；
                // 极少数场景由协调线程执行。
                log.warn("Workflow worker queue rejected execution {}; running inline",
                        executionId, e);
                executionTask.run();
            }
        }

        if (!candidates.isEmpty() && candidates.size() >= limit && claimedCount > 0) {
            synchronized (timerLock) {
                dueWithoutProgress = 0;
            }
            schedulePollAfter(BATCH_DRAIN_DELAY_MS);
            return;
        }
        schedulePersistedWakeup(claimedCount > 0);
    }

    private int availableDispatchCapacity() {
        ThreadPoolExecutor executor = taskExecutor.getThreadPoolExecutor();
        int availableThreads = Math.max(0, executor.getMaximumPoolSize() - executor.getActiveCount());
        int availableQueue = executor.getQueue().remainingCapacity();
        long capacity = (long) availableThreads + availableQueue;
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    private void schedulePersistedWakeup(boolean madeProgress) {
        long observedRevision;
        synchronized (timerLock) {
            observedRevision = timerRevision;
        }

        Date nextActionTime;
        try {
            nextActionTime = executionMapper.selectNextWorkerActionTime();
        } catch (RuntimeException e) {
            log.warn("Unable to schedule next workflow action", e);
            scheduleRefreshAfter(nextDueRetryDelay());
            return;
        }

        synchronized (timerLock) {
            if (observedRevision != timerRevision) {
                return;
            }
            Instant now = Instant.now();
            if (nextActionTime == null) {
                dueWithoutProgress = 0;
                long delayMs = nextIdleReconcileDelayLocked();
                replaceScheduledWakeupLocked(now.plusMillis(delayMs), false);
                log.debug("Workflow worker idle; next safety reconciliation in {} ms", delayMs);
                return;
            }

            Instant actionAt = nextActionTime.toInstant();
            idleReconcileDelayMs = properties.getWorkerIdleReconcileInitialMs();
            if (!actionAt.isAfter(now)) {
                long delayMs;
                if (madeProgress) {
                    dueWithoutProgress = 0;
                    delayMs = BATCH_DRAIN_DELAY_MS;
                } else {
                    delayMs = nextDueRetryDelayLocked();
                }
                replaceScheduledWakeupLocked(now.plusMillis(delayMs), true);
                return;
            }

            dueWithoutProgress = 0;
            long safetyDelayMs = properties.getWorkerIdleReconcileMaxMs();
            Instant safetyAt = now.plusMillis(safetyDelayMs);
            if (actionAt.isAfter(safetyAt)) {
                replaceScheduledWakeupLocked(safetyAt, false);
            } else {
                replaceScheduledWakeupLocked(actionAt, true);
            }
        }
    }

    private void registerTimer(Instant wakeAt) {
        if (!workerEnabled()) {
            return;
        }
        synchronized (timerLock) {
            timerRevision++;
            resetBackoffLocked();
            if (nextWakeAt == null || wakeAt.isBefore(nextWakeAt)) {
                replaceScheduledWakeupLocked(wakeAt, true);
            }
        }
    }

    private void schedulePollAfter(long delayMs) {
        synchronized (timerLock) {
            timerRevision++;
            replaceScheduledWakeupLocked(Instant.now().plusMillis(delayMs), true);
        }
    }

    private void scheduleRefreshAfter(long delayMs) {
        synchronized (timerLock) {
            timerRevision++;
            replaceScheduledWakeupLocked(Instant.now().plusMillis(delayMs), false);
        }
    }

    private void scheduleCoordinatorRetry() {
        try {
            timerExecutor.schedule(this::submitCoordinator,
                    COORDINATOR_RETRY_MS, TimeUnit.MILLISECONDS);
        } catch (RuntimeException retryError) {
            log.error("Workflow coordinator retry rejected", retryError);
        }
    }

    private long nextIdleReconcileDelayLocked() {
        long current = Math.max(properties.getWorkerIdleReconcileInitialMs(),
                idleReconcileDelayMs);
        long maximum = properties.getWorkerIdleReconcileMaxMs();
        idleReconcileDelayMs = Math.min(maximum, saturatedDouble(current));
        return Math.min(current, maximum);
    }

    private long nextDueRetryDelay() {
        synchronized (timerLock) {
            return nextDueRetryDelayLocked();
        }
    }

    private long nextDueRetryDelayLocked() {
        int exponent = Math.min(dueWithoutProgress, 7);
        dueWithoutProgress = Math.min(dueWithoutProgress + 1, 8);
        return Math.min(DUE_RETRY_MAX_MS, DUE_RETRY_BASE_MS << exponent);
    }

    private void resetBackoffLocked() {
        dueWithoutProgress = 0;
        idleReconcileDelayMs = properties.getWorkerIdleReconcileInitialMs();
    }

    private static long saturatedDouble(long value) {
        return value > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : value * 2L;
    }

    private void replaceScheduledWakeupLocked(Instant wakeAt, boolean pollAtWake) {
        if (nextWakeAt != null
                && pollAtNextWake == pollAtWake
                && Math.abs(nextWakeAt.toEpochMilli() - wakeAt.toEpochMilli()) <= 1L) {
            return;
        }
        cancelScheduledWakeupLocked();
        nextWakeAt = wakeAt;
        pollAtNextWake = pollAtWake;
        long delayMillis = delayMillisUntil(Instant.now(), wakeAt);
        try {
            nextWakeTask = timerExecutor.schedule(
                    () -> fireTimer(wakeAt, pollAtWake), delayMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            nextWakeAt = null;
            nextWakeTask = null;
            log.warn("Workflow timer wakeup rejected", e);
            scheduleCoordinatorRetry();
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

    private void fireTimer(Instant wakeAt, boolean pollAtWake) {
        synchronized (timerLock) {
            if (!wakeAt.equals(nextWakeAt) || pollAtNextWake != pollAtWake) {
                return;
            }
            nextWakeAt = null;
            nextWakeTask = null;
            timerRevision++;
        }
        if (pollAtWake) {
            requestPoll(false);
        } else {
            requestScheduleRefresh(false);
        }
    }

    private boolean workerEnabled() {
        return properties.isEnabled() && properties.isWorkerEnabled();
    }
}
