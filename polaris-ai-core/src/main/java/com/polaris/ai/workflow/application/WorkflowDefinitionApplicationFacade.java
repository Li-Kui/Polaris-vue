package com.polaris.ai.workflow.application;

import com.polaris.ai.workflow.definition.WorkflowCompilationResult;
import com.polaris.ai.workflow.spi.WorkflowNodeDescriptor;

import java.util.Collection;
import java.util.List;

/** 管理端和中台控制器共用的应用边界。 */
public interface WorkflowDefinitionApplicationFacade {

    WorkflowDefinitionView createDraft(WorkflowDraftCommand command);

    WorkflowDefinitionView updateDraft(Long definitionId, WorkflowDraftCommand command);

    WorkflowDefinitionView getDefinition(Long definitionId);

    List<WorkflowDefinitionView> listDefinitions();

    WorkflowCompilationResult validateDraft(Long definitionId);

    WorkflowPublishResult publish(Long definitionId, WorkflowPublishCommand command);

    List<WorkflowPublishedVersionView> listVersions(Long definitionId);

    WorkflowPublishedVersionDetailView getVersion(Long definitionId, String versionId);

    WorkflowDefinitionView rollbackDraft(
            Long definitionId, String versionId, WorkflowRollbackCommand command);

    WorkflowDefinitionView cloneDefinition(Long definitionId, WorkflowCloneCommand command);

    Collection<WorkflowNodeDescriptor> listNodeDescriptors();
}
