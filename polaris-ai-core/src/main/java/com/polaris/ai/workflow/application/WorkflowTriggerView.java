package com.polaris.ai.workflow.application;

import java.util.Date;

/** 不含凭据且对调用方安全的触发器视图。 */
public record WorkflowTriggerView(
        String triggerId,
        Long tenantId,
        Long definitionId,
        String workflowVersionId,
        String triggerType,
        String configJson,
        String dedupPolicyJson,
        String status,
        Date nextFireTime,
        Date lastFireTime,
        String lastExecutionId,
        String lastTriggerStatus,
        String lastErrorMessage,
        Integer lockVersion,
        Date createTime,
        Date updateTime) {
}
