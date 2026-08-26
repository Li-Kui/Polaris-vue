package com.polaris.ai.workflow.spi;

import com.polaris.ai.workflow.application.WorkflowResourceOption;

import java.util.List;

/** 发布和执行阶段使用的租户感知逻辑资源提供器。 */
public interface WorkflowResourceProvider {

    String kind();

    List<String> validate(WorkflowResourceRequest request);

    ResolvedWorkflowResource resolve(WorkflowResourceRequest request);

    /** 返回当前调用主体可以选择的现有资源安全摘要。 */
    default List<WorkflowResourceOption> listAvailable(
            WorkflowResourceCatalogRequest request) {
        return List.of();
    }
}
