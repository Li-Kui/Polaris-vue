package com.polaris.ai.workflow.application;

import java.util.List;

/** 为管理端和租户端提供当前身份范围内的工作流资源目录。 */
public interface WorkflowResourceCatalogApplicationFacade {

    List<WorkflowResourceOption> list(String kind, String environment);
}
