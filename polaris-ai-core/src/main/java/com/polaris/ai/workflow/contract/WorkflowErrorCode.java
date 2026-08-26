package com.polaris.ai.workflow.contract;

/** 工作流稳定错误码；错误消息支持本地化且不属于错误码契约。 */
public enum WorkflowErrorCode {
    DEFINITION_INVALID(WorkflowErrorCategory.VALIDATION, false),
    PLAN_INCOMPATIBLE(WorkflowErrorCategory.CONFIGURATION, false),
    EXPRESSION_INVALID(WorkflowErrorCategory.VALIDATION, false),
    EXPRESSION_EVALUATION_FAILED(WorkflowErrorCategory.BUSINESS, false),
    PERMISSION_DENIED(WorkflowErrorCategory.AUTHORIZATION, false),
    TENANT_CONTEXT_MISSING(WorkflowErrorCategory.AUTHORIZATION, false),
    RESOURCE_NOT_FOUND(WorkflowErrorCategory.RESOURCE, false),
    RESOURCE_DISABLED(WorkflowErrorCategory.RESOURCE, false),
    RESOURCE_TEMPORARILY_UNAVAILABLE(WorkflowErrorCategory.TEMPORARY, true),
    QUOTA_EXCEEDED(WorkflowErrorCategory.RATE_LIMIT, false),
    RATE_LIMITED(WorkflowErrorCategory.RATE_LIMIT, true),
    EXECUTION_TIMEOUT(WorkflowErrorCategory.TIMEOUT, false),
    NODE_TIMEOUT(WorkflowErrorCategory.TIMEOUT, true),
    NODE_CANCELLED(WorkflowErrorCategory.CANCELLED, false),
    APPROVAL_REJECTED(WorkflowErrorCategory.BUSINESS, false),
    APPROVAL_EXPIRED(WorkflowErrorCategory.TIMEOUT, false),
    SIDE_EFFECT_UNCONFIRMED(WorkflowErrorCategory.BUSINESS, false),
    EXECUTION_CONFLICT(WorkflowErrorCategory.TEMPORARY, true),
    INTERNAL_ERROR(WorkflowErrorCategory.INTERNAL, false);

    private final WorkflowErrorCategory category;
    private final boolean retryableByDefault;

    WorkflowErrorCode(WorkflowErrorCategory category, boolean retryableByDefault) {
        this.category = category;
        this.retryableByDefault = retryableByDefault;
    }

    public WorkflowErrorCategory getCategory() {
        return category;
    }

    public boolean isRetryableByDefault() {
        return retryableByDefault;
    }
}
