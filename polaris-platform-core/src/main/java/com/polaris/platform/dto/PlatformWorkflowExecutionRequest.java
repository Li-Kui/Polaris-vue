package com.polaris.platform.dto;

/** 通过 API 密钥启动已发布工作流时使用的不含密钥输入。 */
public record PlatformWorkflowExecutionRequest(
        Object input,
        String environment) {
}
