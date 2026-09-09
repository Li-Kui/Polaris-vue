package com.polaris.ai.workflow.application;

import java.util.Date;

/** 对调用方安全的工作流定义视图。 */
public record WorkflowDefinitionView(
        Long id,
        Long tenantId,
        String ownerType,
        String workflowCode,
        String workflowName,
        String description,
        String draftSchemaVersion,
        String draftJson,
        Long draftRevision,
        String currentPublishedVersionId,
        String status,
        Integer lockVersion,
        Date createTime,
        Date updateTime) {
}
