package com.polaris.ai.workflow.runtime;

/** 只携带调度标识，提交后唤醒等待的父执行。 */
public record WorkflowChildChanged(String parentExecutionId, String executionId, boolean terminal) {}
