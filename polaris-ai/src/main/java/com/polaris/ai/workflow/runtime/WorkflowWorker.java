package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.application.WorkflowTaskSignal;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** 轮询持久化执行，并且只调度成功取得隔离令牌的任务。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.workflow", name = "enabled", havingValue = "true")
public class WorkflowWorker {

    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowExecutionEngine executionEngine;
    private final WorkflowProperties properties;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final String runnerId;
    private final AtomicBoolean signalPending = new AtomicBoolean(false);

    public WorkflowWorker(
            WorkflowExecutionMapper executionMapper,
            WorkflowExecutionEngine executionEngine,
            WorkflowProperties properties,
            @Qualifier("workflowTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.executionMapper = executionMapper;
        this.executionEngine = executionEngine;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
        this.runnerId = ManagementFactory.getRuntimeMXBean().getName()
                + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Scheduled(
            fixedDelayString = "${ai.workflow.worker-poll-ms:30000}",
            initialDelayString = "${ai.workflow.worker-poll-ms:30000}")
    public void poll() {
        signalPending.set(false);
        if (!properties.isEnabled() || !properties.isWorkerEnabled()) {
            return;
        }
        for (String executionId : executionMapper.selectClaimCandidates(
                properties.getWorkerBatchSize())) {
            if (executionMapper.claimLease(
                    executionId, runnerId, properties.getLeaseSeconds()) != 1) {
                continue;
            }
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
    }

    /** 事务提交后异步唤醒，带防抖。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskSignal(WorkflowTaskSignal signal) {
        if (signalPending.compareAndSet(false, true)) {
            taskExecutor.execute(this::poll);
        }
    }
}
