package com.polaris.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 数据源连接测试结果。 */
@Schema(description = "数据源连接测试结果")
public record DatasourceTestResponse(
        boolean success,
        long responseTimeMs,
        String databaseProductName,
        String databaseProductVersion,
        String errorCode,
        String errorMessage) {
}
