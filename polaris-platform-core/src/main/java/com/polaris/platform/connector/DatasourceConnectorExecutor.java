package com.polaris.platform.connector;

import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.PlatformDatasource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.temporal.TemporalAccessor;
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
            SQLException sqlError = findSqlException(e);
            log.warn("执行数据源只读查询失败: datasourceId={}, error={}, sqlState={}, vendorCode={}",
                    datasource.getId(), e.getClass().getSimpleName(),
                    sqlError == null ? null : sqlError.getSQLState(),
                    sqlError == null ? null : sqlError.getErrorCode());
            int connectTimeout = datasource.getConnectTimeoutSeconds() == null
                    ? 5 : datasource.getConnectTimeoutSeconds();
            throw new ServiceException(queryFailureMessage(
                    e, timeout, Math.max(1, Math.min(connectTimeout, 30))));
        }
    }

    /** 将 JDBC/连接池异常转换为可操作且不泄露连接凭据的提示。 */
    static String queryFailureMessage(
            Throwable error, int queryTimeoutSeconds, int connectTimeoutSeconds) {
        SQLException sqlError = findSqlException(error);
        if (sqlError == null) {
            return error instanceof IllegalArgumentException
                    ? "数据库返回了无法解析的数据，请检查日期时间等字段类型"
                    : "数据库查询结果处理失败，请联系管理员并提供本次运行 ID";
        }
        String sqlState = sqlError.getSQLState();
        if (sqlError instanceof SQLTimeoutException || "57014".equals(sqlState)) {
            return "数据库查询超时（" + queryTimeoutSeconds
                    + " 秒），请优化 SQL、减少返回数据或调大查询超时";
        }
        if (sqlError instanceof SQLInvalidAuthorizationSpecException
                || startsWith(sqlState, "28")) {
            return "数据库认证失败，请检查用户名、密码及只读账号权限";
        }
        if (sqlError instanceof SQLTransientConnectionException
                || sqlError instanceof SQLNonTransientConnectionException
                || startsWith(sqlState, "08")) {
            return "无法连接数据库（等待 " + connectTimeoutSeconds
                    + " 秒超时），请检查地址、端口、网络及数据库状态";
        }
        if (sqlError instanceof SQLSyntaxErrorException || startsWith(sqlState, "42")) {
            return withDatabaseDetail("SQL 语法或对象访问错误", sqlError.getMessage());
        }
        if (sqlError instanceof SQLDataException || startsWith(sqlState, "22")) {
            return withDatabaseDetail("数据库数据转换失败", sqlError.getMessage());
        }
        if (startsWith(sqlState, "40")) {
            return "数据库发生并发冲突或死锁，请稍后重试";
        }
        if (sqlError instanceof SQLFeatureNotSupportedException) {
            return "数据库驱动不支持当前查询能力，请调整 SQL 或升级驱动";
        }
        return sqlState == null || sqlState.isBlank()
                ? "数据库查询执行失败，请联系管理员并提供本次运行 ID"
                : "数据库查询执行失败（SQLState " + safeSqlState(sqlState)
                        + "），请联系管理员并提供本次运行 ID";
    }

    private static SQLException findSqlException(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        while (current != null && visited.add(current)) {
            if (current instanceof SQLException sqlException) return sqlException;
            current = current.getCause();
        }
        return null;
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }

    private static String withDatabaseDetail(String summary, String detail) {
        if (detail == null || detail.isBlank()) return summary;
        String safe = detail.replaceAll(
                        "(?i)(password|pwd|token|secret)\\s*[:=]\\s*[^,;\\s]+",
                        "$1=[REDACTED]")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        if (safe.length() > 240) safe = safe.substring(0, 240) + "…";
        return summary + "：" + safe;
    }

    private static String safeSqlState(String sqlState) {
        String safe = sqlState.replaceAll("[^A-Za-z0-9]", "");
        return safe.length() > 8 ? safe.substring(0, 8) : safe;
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
                Object value = normalizeJdbcValue(
                        resultSet.getObject(index), metadata.getColumnType(index));
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

    /**
     * JDBC 驱动会为日期时间、数组等列返回驱动相关对象；在连接器边界统一转换为
     * 稳定的 JSON 兼容值，确保实际输出与 JDBC 元数据生成的 Schema 一致。
     */
    static Object normalizeJdbcValue(Object value, int jdbcType) throws SQLException {
        if (value == null) return null;
        if (value instanceof java.sql.Date date) return date.toLocalDate().toString();
        if (value instanceof java.sql.Time time) return time.toLocalTime().toString();
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof TemporalAccessor temporal) return temporal.toString();
        if (value instanceof java.util.Date date) return date.toInstant().toString();
        if (value instanceof Calendar calendar) return calendar.toInstant().toString();
        if (value instanceof java.sql.Array sqlArray) {
            try {
                return normalizeJdbcArray(sqlArray.getArray());
            } finally {
                sqlArray.free();
            }
        }
        if (value instanceof SQLXML sqlxml) {
            try {
                return sqlxml.getString();
            } finally {
                sqlxml.free();
            }
        }
        if (value instanceof RowId || value instanceof UUID) return value.toString();
        if (jdbcType == Types.DATE || jdbcType == Types.TIME
                || jdbcType == Types.TIME_WITH_TIMEZONE
                || jdbcType == Types.TIMESTAMP
                || jdbcType == Types.TIMESTAMP_WITH_TIMEZONE) {
            return value.toString();
        }
        return value;
    }

    private static List<Object> normalizeJdbcArray(Object array) throws SQLException {
        if (array == null) return List.of();
        if (!array.getClass().isArray()) {
            return List.of(normalizeJdbcValue(array, Types.JAVA_OBJECT));
        }
        int length = java.lang.reflect.Array.getLength(array);
        List<Object> values = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            values.add(normalizeJdbcValue(
                    java.lang.reflect.Array.get(array, index), Types.JAVA_OBJECT));
        }
        return values;
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
