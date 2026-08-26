package com.polaris.ai.workflow.contract;

/** 供重试策略和用户诊断策略使用的稳定错误分类。 */
public enum WorkflowErrorCategory {
    VALIDATION,
    CONFIGURATION,
    AUTHORIZATION,
    RESOURCE,
    RATE_LIMIT,
    TEMPORARY,
    BUSINESS,
    TIMEOUT,
    CANCELLED,
    INTERNAL
}
