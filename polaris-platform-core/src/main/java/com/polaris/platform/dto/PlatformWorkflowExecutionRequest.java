package com.polaris.platform.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** 通过 API 密钥启动已发布工作流时使用的不含密钥输入。 */
public record PlatformWorkflowExecutionRequest(
        JsonNode input,
        String environment) {
}
