package com.polaris.ai.workflow.spi;

/**
 * 工作流资源引用检查器。
 *
 * <p>资源提供模块通过该接口检查资源是否仍被工作流绑定使用，避免误删共享资源。</p>
 */
public interface WorkflowResourceUsageInspector {

    long countActiveUsages(Long tenantId, String resourceKind, String resourceId);
}
