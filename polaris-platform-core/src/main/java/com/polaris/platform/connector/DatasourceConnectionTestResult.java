package com.polaris.platform.connector;

/** 内部数据源连接测试结果。 */
public record DatasourceConnectionTestResult(
        boolean success,
        long responseTimeMs,
        String databaseProductName,
        String databaseProductVersion,
        String errorCode,
        String errorMessage) {
}
