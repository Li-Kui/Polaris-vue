package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.application.WorkflowOutboxEvent;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.domain.WorkflowOutbox;
import com.polaris.ai.workflow.mapper.WorkflowOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.UUID;

/** 使用可过期领取机制对事务发件箱记录进行至少一次投递。 */
@Slf4j
@Component
public class WorkflowOutboxPublisher {

    private final WorkflowOutboxMapper outboxMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkflowProperties properties;
    private final String publisherId;

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

    @Scheduled(fixedDelayString = "${ai.workflow.outbox-poll-ms:1000}")
    public void publishPending() {
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
}
