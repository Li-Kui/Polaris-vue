package com.polaris.ai.workflow.application;

import java.util.Date;

/** 不可变发布版本摘要。 */
public record WorkflowPublishedVersionView(
        String versionId,
        Long definitionId,
        Integer versionNo,
        String schemaVersion,
        String planSchemaVersion,
        String contentHash,
        String status,
        String publishedBy,
        Date publishedTime) {
}
