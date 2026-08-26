package com.polaris.ai.workflow.definition;

/** 可在可视化编辑器中定位的结构化编译诊断。 */
public record WorkflowDiagnostic(
        Severity severity,
        String code,
        String nodeId,
        String fieldPath,
        String message) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public static WorkflowDiagnostic error(
            String code, String nodeId, String fieldPath, String message) {
        return new WorkflowDiagnostic(Severity.ERROR, code, nodeId, fieldPath, message);
    }

    public static WorkflowDiagnostic warning(
            String code, String nodeId, String fieldPath, String message) {
        return new WorkflowDiagnostic(Severity.WARNING, code, nodeId, fieldPath, message);
    }
}
