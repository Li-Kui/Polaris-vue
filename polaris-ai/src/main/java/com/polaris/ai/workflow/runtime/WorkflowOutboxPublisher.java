package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.application.WorkflowOutboxEvent;
import com.polaris.ai.workflow.application.WorkflowTaskSignal;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.domain.WorkflowOutbox;
import com.polaris.ai.workflow.mapper.WorkflowOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.management.ManagementFactory;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** 使用可过期领取机制对事务发件箱记录进行至少一次投递。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.workflow", name = "enabled", havingValue = "true")
public class WorkflowOutboxPublisher {

    private final WorkflowOutboxMapper outboxMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkflowProperties properties;
    private final String publisherId;
    private final AtomicBoolean signalPending = new AtomicBoolean(false);

    public WorkflowOutboxPublisher(
            WorkflowOutboxMapper outboxMapper,
            ApplicationEventPublisher eventPublisher,
            WorkflowProperties properties) {
        this.outboxMapper = outboxMapper;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.publisherId = ManagementFactory.getRuntimeMXBean().getName()
                + ":outbox:" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Scheduled(fixedDelayString = "${ai.workflow.outbox-poll-ms:30000}")
    public void publishPending() {
        signalPending.set(false);
        if (!properties.isEnabled()) {
            return;
        }
        for (Long id : outboxMapper.selectPublishCandidates(100)) {
            if (outboxMapper.claim(id, publisherId) != 1) {
                continue;
            }
            WorkflowOutbox outbox = outboxMapper.selectById(id);
            if (outbox == null || !publisherId.equals(outbox.getClaimedBy())) {
                continue;
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
    }

    /** 事务提交后异步唤醒，带防抖。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskSignal(WorkflowTaskSignal signal) {
        if (signalPending.compareAndSet(false, true)) {
            CompletableFuture.runAsync(this::publishPending);
        }
    }
}
