package com.polaris.platform.dto;

import java.util.Date;

/** 分享运行时使用的脱敏执行视图。 */
public record WorkflowShareExecutionView(
        String executionId,
        String status,
        String outputJson,
        String errorMessage,
        Long eventSequence,
        Boolean cancelRequested,
        Date createTime,
        Date startTime,
        Date finishTime) {
}
