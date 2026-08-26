package com.polaris.ai.workflow.application;

import com.polaris.ai.workflow.definition.WorkflowDiagnostic;

import java.util.List;

/** 发布结果；草稿无效时返回诊断且不创建版本。 */
public record WorkflowPublishResult(
        boolean published,
        WorkflowPublishedVersionView version,
        List<WorkflowDiagnostic> diagnostics) {

    public WorkflowPublishResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
