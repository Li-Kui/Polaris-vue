package com.polaris.ai.workflow.application;

import java.util.Date;

/** 对调用方安全的工作流产物元数据。 */
public record WorkflowArtifactView(
        String artifactId,
        String executionId,
        String nodeRunId,
        String fileName,
        String mimeType,
        Long sizeBytes,
        String contentHash,
        Date expiresTime,
        Date createTime) {
}
