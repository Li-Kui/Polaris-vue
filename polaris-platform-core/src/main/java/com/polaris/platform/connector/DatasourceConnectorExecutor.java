package com.polaris.platform.connector;

import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.PlatformDatasource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 外部数据库只读连接与查询执行器。 */
@Slf4j
@Component
public class DatasourceConnectorExecutor {

    private static final int MAX_ROWS = 1000;
    private static final int MAX_COLUMNS = 100;
    private static final long MAX_RESULT_BYTES = 1024L * 1024L;
    private static final int MAX_CELL_BYTES = 64 * 1024;
    private static final long METADATA_CACHE_TTL_MS = 5 * 60_000L;
    private static final int MAX_METADATA_CACHE_ENTRIES = 512;

    private final ReadOnlySqlPolicy sqlPolicy;
    private final NamedSqlParameters namedSqlParameters;
    private final DatasourceJdbcUrlFactory jdbcUrlFactory;
    private final DatasourceHostSafetyPolicy hostSafetyPolicy;
    private final ConnectorCredentialCipher credentialCipher;
    private final DatasourceConnectionPoolManager connectionPoolManager;
    private final Map<String, CachedQueryMetadata> metadataCache = new ConcurrentHashMap<>();

    public DatasourceConnectorExecutor(
            ReadOnlySqlPolicy sqlPolicy,
            NamedSqlParameters namedSqlParameters,
            DatasourceJdbcUrlFactory jdbcUrlFactory,
            DatasourceHostSafetyPolicy hostSafetyPolicy,
            ConnectorCredentialCipher credentialCipher,
            DatasourceConnectionPoolManager connectionPoolManager) {
        this.sqlPolicy = sqlPolicy;
        this.namedSqlParameters = namedSqlParameters;
        this.jdbcUrlFactory = jdbcUrlFactory;
        this.hostSafetyPolicy = hostSafetyPolicy;
        this.credentialCipher = credentialCipher;
        this.connectionPoolManager = connectionPoolManager;
    }

    public DatasourceConnectionTestResult testConnection(PlatformDatasource datasource) {
        long startedAt = System.nanoTime();
        try (Connection connection = getDirectConnection(datasource)) {
            DatabaseMetaData metadata = connection.getMetaData();
            return new DatasourceConnectionTestResult(
                    true, elapsedMillis(startedAt), metadata.getDatabaseProductName(),
                    metadata.getDatabaseProductVersion(), null, null);
        } catch (Exception e) {
            log.warn("测试数据源连接失败: type={}, target={}, error={}",
                    datasource.getDsType(), safeTarget(datasource), e.getClass().getSimpleName());
            return new DatasourceConnectionTestResult(
                    false, elapsedMillis(startedAt), null, null,
                    "CONNECTION_FAILED", "无法连接数据库，请检查地址、账号、密码和网络");
        }
    }

    public List<Map<String, Object>> executeQuery(
            PlatformDatasource datasource,
            String sql,
            Map<String, Object> parameters,
            int maxRows,
            Integer requestedTimeoutSeconds) {
        String safeSql = sqlPolicy.validate(sql);
        NamedSqlParameters.ParsedSql parsed = namedSqlParameters.parse(safeSql);
        Map<String, Object> safeParameters = parameters == null ? Map.of() : parameters;
        for (String name : parsed.parameterNames()) {
            if (!safeParameters.containsKey(name)) {
                throw new ServiceException("缺少 SQL 参数: " + name);
            }
        }
        int rowLimit = maxRows > 0 ? Math.min(maxRows, MAX_ROWS) : 100;
        Integer configuredTimeout = requestedTimeoutSeconds == null
                ? datasource.getQueryTimeoutSeconds() : requestedTimeoutSeconds;
        int timeout = configuredTimeout == null ? 10 : configuredTimeout;
        timeout = Math.max(1, Math.min(timeout, 30));
        try (Connection connection = connectionPoolManager.getConnection(datasource)) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(parsed.sql())) {
                statement.setQueryTimeout(timeout);
                statement.setMaxRows(rowLimit);
                bind(statement, parsed.parameterNames(), safeParameters);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return readRows(resultSet);
                }
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("执行数据源只读查询失败: datasourceId={}, error={}",
                    datasource.getId(), e.getClass().getSimpleName());
            throw new ServiceException("数据库查询执行失败");
        }
    }

    /**
     * 只 prepare SQL 并读取 JDBC 列元数据，不执行查询、不拉取业务数据。
     */
    public DatasourceQueryMetadata inspectQuery(
            PlatformDatasource datasource, String sql) {
        String safeSql = sqlPolicy.validate(sql);
        NamedSqlParameters.ParsedSql parsed = namedSqlParameters.parse(safeSql);
        String cacheKey = metadataCacheKey(datasource, parsed.sql());
        long now = System.currentTimeMillis();
        CachedQueryMetadata cached = metadataCache.get(cacheKey);
        if (cached != null && cached.expiresAt() > now) {
            return cached.metadata();
        }
        try (Connection connection = connectionPoolManager.getConnection(datasource)) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(parsed.sql())) {
                int timeout = datasource.getQueryTimeoutSeconds() == null
                        ? 10 : datasource.getQueryTimeoutSeconds();
                statement.setQueryTimeout(Math.max(1, Math.min(timeout, 30)));
                ResultSetMetaData metadata = statement.getMetaData();
                if (metadata == null) {
                    throw new ServiceException("数据库驱动不支持预执行列元数据解析");
                }
                DatasourceQueryMetadata result = readMetadata(metadata);
                pruneMetadataCache(now);
                metadataCache.put(cacheKey,
                        new CachedQueryMetadata(result, now + METADATA_CACHE_TTL_MS));
                return result;
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析数据源查询元数据失败: datasourceId={}, error={}",
                    datasource.getId(), e.getClass().getSimpleName());
            throw new ServiceException("数据库查询元数据解析失败");
        }
    }

    public List<Map<String, Object>> executeQuery(
            PlatformDatasource datasource, String sql, int maxRows) {
        return executeQuery(datasource, sql, Map.of(), maxRows, null);
    }

    private Connection getDirectConnection(PlatformDatasource datasource) throws SQLException {
        hostSafetyPolicy.validate(datasource.getHost());
        String password = credentialCipher.decrypt(datasource.getPassword());
        if (password == null) throw new ServiceException("数据库密码尚未配置");
        return java.sql.DriverManager.getConnection(
                jdbcUrlFactory.build(datasource), datasource.getUsername(), password);
    }

    private void bind(
            PreparedStatement statement,
            List<String> parameterNames,
            Map<String, Object> parameters) throws SQLException {
        for (int index = 0; index < parameterNames.size(); index++) {
            statement.setObject(index + 1, parameters.get(parameterNames.get(index)));
        }
    }

    private List<Map<String, Object>> readRows(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        if (columnCount > MAX_COLUMNS) {
            throw new ServiceException("查询结果列数超过 " + MAX_COLUMNS + " 列限制");
        }
        Set<String> labels = new HashSet<>();
        for (int index = 1; index <= columnCount; index++) {
            String label = metadata.getColumnLabel(index);
            if (!labels.add(label.toLowerCase(java.util.Locale.ROOT))) {
                throw new ServiceException("查询结果包含重复列名，请使用 SQL 别名区分");
            }
            if (binaryType(metadata.getColumnType(index))) {
                throw new ServiceException("查询结果不能包含 BLOB、CLOB 或二进制大字段");
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        long estimatedBytes = 0;
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int index = 1; index <= columnCount; index++) {
                Object value = resultSet.getObject(index);
                if (value instanceof Blob || value instanceof Clob || value instanceof byte[]) {
                    throw new ServiceException("查询结果不能包含 BLOB、CLOB 或二进制大字段");
                }
                int cellBytes = value == null ? 0
                        : String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (cellBytes > MAX_CELL_BYTES) {
                    throw new ServiceException("查询结果单个字段超过 64KB 限制");
                }
                estimatedBytes += cellBytes;
                if (estimatedBytes > MAX_RESULT_BYTES) {
                    throw new ServiceException("查询结果超过 1MB 限制");
                }
                row.put(metadata.getColumnLabel(index), value);
            }
            rows.add(row);
        }
        return rows;
    }

    private DatasourceQueryMetadata readMetadata(ResultSetMetaData metadata) throws Exception {
        int columnCount = metadata.getColumnCount();
        if (columnCount > MAX_COLUMNS) {
            throw new ServiceException("查询结果列数超过 " + MAX_COLUMNS + " 列限制");
        }
        Set<String> labels = new HashSet<>();
        List<DatasourceQueryMetadata.Column> columns = new ArrayList<>();
        for (int index = 1; index <= columnCount; index++) {
            String label = metadata.getColumnLabel(index);
            if (label == null || label.isBlank()) label = metadata.getColumnName(index);
            if (label == null || label.isBlank()) label = "column_" + index;
            if (!labels.add(label.toLowerCase(Locale.ROOT))) {
                throw new ServiceException("查询结果包含重复列名，请使用 SQL 别名区分");
            }
            int jdbcType = metadata.getColumnType(index);
            if (binaryType(jdbcType)) {
                throw new ServiceException("查询结果不能包含 BLOB、CLOB 或二进制大字段");
            }
            String nativeType = metadata.getColumnTypeName(index);
            JdbcJsonType jsonType = jsonType(jdbcType, nativeType);
            columns.add(new DatasourceQueryMetadata.Column(
                    label, jsonType.type(), jsonType.format(),
                    metadata.isNullable(index) != ResultSetMetaData.columnNoNulls,
                    nativeType));
        }
        String signature = columns.stream()
                .map(column -> String.join(":",
                        column.name(), column.jsonType(), String.valueOf(column.format()),
                        String.valueOf(column.nullable()), String.valueOf(column.nativeType())))
                .reduce((left, right) -> left + "|" + right)
                .orElse("empty");
        return new DatasourceQueryMetadata(columns, sha256(signature));
    }

    private JdbcJsonType jsonType(int jdbcType, String nativeType) {
        return switch (jdbcType) {
            case Types.BOOLEAN, Types.BIT -> new JdbcJsonType("boolean", null);
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT ->
                    new JdbcJsonType("integer", null);
            case Types.NUMERIC, Types.DECIMAL, Types.REAL, Types.FLOAT, Types.DOUBLE ->
                    new JdbcJsonType("number", null);
            case Types.DATE -> new JdbcJsonType("string", "date");
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> new JdbcJsonType("string", "time");
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
                    new JdbcJsonType("string", "date-time");
            case Types.ARRAY -> new JdbcJsonType("array", null);
            case Types.OTHER -> nativeType != null
                    && Set.of("json", "jsonb").contains(nativeType.toLowerCase(Locale.ROOT))
                    ? new JdbcJsonType("object", null)
                    : new JdbcJsonType("string", null);
            default -> new JdbcJsonType("string", null);
        };
    }

    private String metadataCacheKey(PlatformDatasource datasource, String sql) {
        return datasource.getId() + ":" + datasource.getConfigVersion()
                + ":" + datasource.getCurrentVersionId() + ":" + sha256(sql);
    }

    private void pruneMetadataCache(long now) {
        if (metadataCache.size() < MAX_METADATA_CACHE_ENTRIES) return;
        metadataCache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        if (metadataCache.size() >= MAX_METADATA_CACHE_ENTRIES) metadataCache.clear();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("无法计算 SQL 元数据摘要", e);
        }
    }

    private boolean binaryType(int type) {
        return type == Types.BLOB || type == Types.CLOB || type == Types.NCLOB
                || type == Types.BINARY || type == Types.VARBINARY
                || type == Types.LONGVARBINARY;
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private String safeTarget(PlatformDatasource datasource) {
        return datasource.getHost() + ":" + datasource.getPort()
                + "/" + datasource.getDatabaseName();
    }

    private record JdbcJsonType(String type, String format) {
    }

    private record CachedQueryMetadata(
            DatasourceQueryMetadata metadata,
            long expiresAt) {
    }
}
