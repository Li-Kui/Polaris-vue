package com.polaris.ai.workflow.application;

/** 分享运行时需要的工作流定义信息。 */
public record WorkflowShareDefinitionView(
        Long definitionId,
        Long tenantId,
        String workflowCode,
        String defaultPageType,
        String recommendedPageType,
        String sharePageConfigJson,
        Object inputSchema,
        Integer lockVersion) {

    public WorkflowShareDefinitionView(Long definitionId, Long tenantId, String workflowCode,
            String defaultPageType, String recommendedPageType, String sharePageConfigJson, Object inputSchema) {
        this(definitionId, tenantId, workflowCode, defaultPageType, recommendedPageType,
                sharePageConfigJson, inputSchema, null);
    }
}
