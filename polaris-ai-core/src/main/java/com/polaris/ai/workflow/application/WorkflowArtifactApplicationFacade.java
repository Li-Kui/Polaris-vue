package com.polaris.ai.workflow.application;

import java.util.List;

/** 工作流私有执行产物的应用边界。 */
public interface WorkflowArtifactApplicationFacade {

    List<WorkflowArtifactView> list(String executionId);

    WorkflowArtifactContent load(String executionId, String artifactId);
}
