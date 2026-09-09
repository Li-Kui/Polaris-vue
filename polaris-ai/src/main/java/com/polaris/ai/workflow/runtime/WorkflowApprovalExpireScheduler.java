package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.service.WorkflowApprovalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 从 WorkflowApprovalService 提取的审批过期定时扫描器，支持条件化加载。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.workflow", name = "enabled", havingValue = "true")
public class WorkflowApprovalExpireScheduler {

    private final WorkflowApprovalService approvalService;

    public WorkflowApprovalExpireScheduler(WorkflowApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Scheduled(fixedDelayString = "${ai.workflow.approval-expire-poll-ms:30000}")
    public void expirePendingTasks() {
        approvalService.expirePendingTasks();
    }
}
