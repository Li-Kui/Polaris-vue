package com.polaris.ai.workflow.definition;

import java.util.List;

/** 编译结果，包含可执行计划或结构化错误。 */
public record WorkflowCompilationResult(
        WorkflowExecutionPlan plan,
        List<WorkflowDiagnostic> diagnostics) {

    public WorkflowCompilationResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean isValid() {
        return plan != null && diagnostics.stream()
                .noneMatch(item -> item.severity() == WorkflowDiagnostic.Severity.ERROR);
    }
}
