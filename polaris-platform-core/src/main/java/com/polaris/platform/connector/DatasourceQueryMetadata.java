package com.polaris.platform.connector;

import java.util.List;

/** 只读 SQL 在不拉取结果集时获得的安全列元数据。 */
public record DatasourceQueryMetadata(
        List<Column> columns,
        String fingerprint) {

    public DatasourceQueryMetadata {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    public record Column(
            String name,
            String jsonType,
            String format,
            boolean nullable,
            String nativeType) {
    }
}
