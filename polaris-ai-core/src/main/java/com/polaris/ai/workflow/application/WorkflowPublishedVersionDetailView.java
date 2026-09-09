package com.polaris.ai.workflow.application;

import java.util.Date;

/** 包含定义内容、可用于查看和对比的不可变发布版本详情。 */
public record WorkflowPublishedVersionDetailView(
        String versionId,
        Long definitionId,
        Integer versionNo,
        String schemaVersion,
        String definitionJson,
        String contentHash,
        String status,
        String publishedBy,
        Date publishedTime) {
}
