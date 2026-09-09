package com.polaris.ai.workflow.application;

/** 使用调用方提供输入隔离试运行一个已保存草稿节点。 */
public record WorkflowNodeTestCommand(
        Object input,
        String environment,
        Integer timeoutSeconds,
        String mode) {

    public WorkflowNodeTestCommand(
            Object input, String environment, Integer timeoutSeconds) {
        this(input, environment, timeoutSeconds, "NODE");
    }
}
