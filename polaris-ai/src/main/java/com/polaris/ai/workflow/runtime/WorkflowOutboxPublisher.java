package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.application.WorkflowOutboxEvent;
import com.polaris.ai.workflow.application.WorkflowOutboxSignal;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.domain.WorkflowOutbox;
import com.polaris.ai.workflow.mapper.WorkflowOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 事务事件即时唤醒、持久化重试时间精准唤醒的至少一次发件箱投递器。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.workflow", name = "enabled", havingValue = "true")
public class WorkflowOutboxPublisher {

    private static final int BATCH_SIZE = 100;
    private static final long BATCH_DRAIN_DELAY_MS = 25L;
    private static final long FAILURE_RETRY_MS = 1000L;

    private final WorkflowOutboxMapper outboxMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkflowProperties properties;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ScheduledExecutorService timerExecutor;
    private final String publisherId;
    private final AtomicBoolean publishRequested = new AtomicBoolean(false);
    private final AtomicBoolean publisherRunning = new AtomicBoolean(false);
    private final Object timerLock = new Object();
    private Instant nextWakeAt;
    private ScheduledFuture<?> nextWakeTask;
    private long timerRevision;
    private long idleReconcileDelayMs;

    public WorkflowOutboxPublisher(
            WorkflowOutboxMapper outboxMapper,
            ApplicationEventPublisher eventPublisher,
            WorkflowProperties properties,
            @Qualifier("workflowTaskExecutor") ThreadPoolTaskExecutor taskExecutor,
            @Qualifier("scheduledExecutorService") ScheduledExecutorService timerExecutor) {
        this.outboxMapper = outboxMapper;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
        this.timerExecutor = timerExecutor;
        this.idleReconcileDelayMs = properties.getOutboxIdleReconcileInitialMs();
        if (timerExecutor instanceof ScheduledThreadPoolExecutor scheduledExecutor) {
            scheduledExecutor.setRemoveOnCancelPolicy(true);
        }
        this.publisherId = ManagementFactory.getRuntimeMXBean().getName()
                + ":outbox:" + UUID.randomUUID().toString().substring(0, 8);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        requestPublish(true);
    }

    /** 保留显式投递入口，实际工作由串行投递器异步执行。 */
    public void publishPending() {
        requestPublish(true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxSignal(WorkflowOutboxSignal signal) {
        requestPublish(true);
    }

    private void requestPublish(boolean resetIdle) {
        if (!properties.isEnabled()) {
            return;
        }
        synchronized (timerLock) {
            timerRevision++;
            cancelWakeupLocked();
            if (resetIdle) {
                idleReconcileDelayMs = properties.getOutboxIdleReconcileInitialMs();
            }
        }
        publishRequested.set(true);
        submitPublisher();
    }

    private void submitPublisher() {
        if (!publisherRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            taskExecutor.execute(this::runPublisher);
        } catch (RuntimeException e) {
            publisherRunning.set(false);
            log.warn("Workflow outbox publisher wakeup rejected; retrying", e);
            scheduleWakeup(FAILURE_RETRY_MS);
        }
    }

    private void runPublisher() {
        try {
            while (properties.isEnabled() && publishRequested.getAndSet(false)) {
                try {
                    publishBatch();
                } catch (RuntimeException e) {
                    log.warn("Unable to publish workflow outbox batch; retrying", e);
                    scheduleWakeup(FAILURE_RETRY_MS);
                }
            }
        } finally {
            publisherRunning.set(false);
            if (properties.isEnabled() && publishRequested.get()) {
                submitPublisher();
            }
        }
    }

    private void publishBatch() {
        List<Long> candidates;
        try {
            candidates = outboxMapper.selectPublishCandidates(BATCH_SIZE);
        } catch (RuntimeException e) {
            log.warn("Unable to select workflow outbox candidates", e);
            scheduleWakeup(FAILURE_RETRY_MS);
            return;
        }

        for (Long id : candidates) {
            publishOne(id);
        }
        if (candidates.size() >= BATCH_SIZE) {
            scheduleWakeup(BATCH_DRAIN_DELAY_MS);
        } else {
            schedulePersistedWakeup();
        }
    }

    private void publishOne(Long id) {
        if (outboxMapper.claim(id, publisherId) != 1) {
            return;
        }
        WorkflowOutbox outbox = outboxMapper.selectById(id);
        if (outbox == null || !publisherId.equals(outbox.getClaimedBy())) {
            return;
        }
        try {
            eventPublisher.publishEvent(new WorkflowOutboxEvent(
                    outbox.getEventId(), outbox.getTenantId(), outbox.getAggregateType(),
                    outbox.getAggregateId(), outbox.getEventType(), outbox.getPayloadJson()));
            outboxMapper.markPublished(id, publisherId);
        } catch (RuntimeException e) {
            int attempt = outbox.getAttemptCount() == null ? 1 : outbox.getAttemptCount();
            int delaySeconds = Math.min(300, 1 << Math.min(8, attempt));
            outboxMapper.markFailed(id, publisherId, delaySeconds);
            log.warn("Workflow outbox publish failed: eventId={}, error={}",
                    outbox.getEventId(), e.getClass().getSimpleName());
        }
    }

    private void schedulePersistedWakeup() {
        long observedRevision;
        synchronized (timerLock) {
            observedRevision = timerRevision;
        }
        Date nextPublishTime;
        try {
            nextPublishTime = outboxMapper.selectNextPublishTime();
        } catch (RuntimeException e) {
            log.warn("Unable to schedule next workflow outbox action", e);
            scheduleWakeup(FAILURE_RETRY_MS);
            return;
        }

        synchronized (timerLock) {
            if (observedRevision != timerRevision) {
                return;
            }
            Instant now = Instant.now();
            if (nextPublishTime == null) {
                long delayMs = nextIdleDelayLocked();
                replaceWakeupLocked(now.plusMillis(delayMs));
                log.debug("Workflow outbox idle; next safety reconciliation in {} ms", delayMs);
                return;
            }
            idleReconcileDelayMs = properties.getOutboxIdleReconcileInitialMs();
            Instant actionAt = nextPublishTime.toInstant();
            if (!actionAt.isAfter(now)) {
                actionAt = now.plusMillis(FAILURE_RETRY_MS);
            }
            Instant safetyAt = now.plusMillis(properties.getOutboxIdleReconcileMaxMs());
            replaceWakeupLocked(actionAt.isAfter(safetyAt) ? safetyAt : actionAt);
        }
    }

    private long nextIdleDelayLocked() {
        long initial = properties.getOutboxIdleReconcileInitialMs();
        long maximum = properties.getOutboxIdleReconcileMaxMs();
        long current = Math.max(initial, idleReconcileDelayMs);
        long doubled = current > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : current * 2L;
        idleReconcileDelayMs = Math.min(maximum, doubled);
        return Math.min(current, maximum);
    }

    private void scheduleWakeup(long delayMs) {
        synchronized (timerLock) {
            timerRevision++;
            replaceWakeupLocked(Instant.now().plusMillis(delayMs));
        }
    }

    private void replaceWakeupLocked(Instant wakeAt) {
        cancelWakeupLocked();
        nextWakeAt = wakeAt;
        long delayMs = delayMillisUntil(Instant.now(), wakeAt);
        try {
            nextWakeTask = timerExecutor.schedule(
                    () -> fireWakeup(wakeAt), delayMs, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            nextWakeAt = null;
            nextWakeTask = null;
            log.warn("Workflow outbox timer rejected", e);
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

    private void fireWakeup(Instant wakeAt) {
        synchronized (timerLock) {
            if (!wakeAt.equals(nextWakeAt)) {
                return;
            }
            nextWakeAt = null;
            nextWakeTask = null;
            timerRevision++;
        }
        requestPublish(false);
    }

    private void cancelWakeupLocked() {
        if (nextWakeTask != null) {
            nextWakeTask.cancel(false);
        }
        nextWakeAt = null;
        nextWakeTask = null;
    }
}
