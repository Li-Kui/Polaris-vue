package com.polaris.ai.workflow.runtime;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 数据库租约心跳、跨实例取消感知与历史数据维护。 */
@Slf4j
@Component
public class WorkflowExecutionLeaseManager {

    private final WorkflowExecutionStore executionStore;
    private final WorkflowCancellationRegistry cancellationRegistry;
    private final int retentionDays;
    private final int approvalExpireDays;

    public WorkflowExecutionLeaseManager(
            WorkflowExecutionStore executionStore,
            WorkflowCancellationRegistry cancellationRegistry,
            @Value("${ai.workflow.retention-days:30}") int retentionDays,
            @Value("${ai.workflow.approval-expire-days:7}") int approvalExpireDays) {
        this.executionStore = executionStore;
        this.cancellationRegistry = cancellationRegistry;
        this.retentionDays = Math.max(1, retentionDays);
        this.approvalExpireDays = Math.max(1, approvalExpireDays);
    }

    @PostConstruct
    public void recoverExpiredExecutionsOnStartup() {
        int recovered = executionStore.failExpiredLeases();
        if (recovered > 0) {
            log.warn("工作流启动恢复已回收 {} 个租约过期执行", recovered);
        }
    }

    @Scheduled(
            fixedDelayString = "${ai.workflow.heartbeat-ms:30000}",
            initialDelayString = "${ai.workflow.heartbeat-ms:30000}")
    public void heartbeat() {
        int expired = executionStore.failExpiredLeases();
        if (expired > 0) {
            log.warn("工作流租约检查已回收 {} 个过期执行", expired);
        }
        for (String executionId : cancellationRegistry.activeExecutionIds()) {
            if (!executionStore.renewLease(executionId)
                    && executionStore.shouldStopLocalExecution(executionId)) {
                cancellationRegistry.cancel(executionId);
            }
        }
    }

    @Scheduled(
            fixedDelayString = "${ai.workflow.maintenance-ms:3600000}",
            initialDelayString = "${ai.workflow.maintenance-ms:3600000}")
    public void maintainExecutionData() {
        int approvals = executionStore.expirePendingApprovals(approvalExpireDays);
        int executions = executionStore.cleanupTerminalExecutions(retentionDays);
        if (approvals > 0 || executions > 0) {
            log.info("工作流数据维护完成: 过期审批 {}, 清理执行 {}", approvals, executions);
        }
    }
}
