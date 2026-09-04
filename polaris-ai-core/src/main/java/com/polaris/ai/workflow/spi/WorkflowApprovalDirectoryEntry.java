package com.polaris.ai.workflow.spi;

/** 审批编辑器可选择的一条用户、角色或部门目录项。 */
public record WorkflowApprovalDirectoryEntry(
        String type,
        String id,
        String name,
        String description,
        boolean available) {
}
