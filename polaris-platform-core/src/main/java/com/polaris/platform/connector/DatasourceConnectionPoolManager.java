package com.polaris.platform.connector;

import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.PlatformDatasource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按数据源连接版本隔离的小型连接池管理器。
 *
 * <p>版本号是缓存键的一部分，连接配置切换后不会污染已经启动任务的执行快照。</p>
 */
@Component
public class DatasourceConnectionPoolManager {

    private static final int MAX_POOLS = 64;
    private final DatasourceJdbcUrlFactory jdbcUrlFactory;
    private final DatasourceHostSafetyPolicy hostSafetyPolicy;
    private final ConnectorCredentialCipher credentialCipher;
    private final Map<String, HikariDataSource> pools = new LinkedHashMap<>(16, 0.75f, true);

    public DatasourceConnectionPoolManager(
            DatasourceJdbcUrlFactory jdbcUrlFactory,
            DatasourceHostSafetyPolicy hostSafetyPolicy,
            ConnectorCredentialCipher credentialCipher) {
        this.jdbcUrlFactory = jdbcUrlFactory;
        this.hostSafetyPolicy = hostSafetyPolicy;
        this.credentialCipher = credentialCipher;
    }

    public Connection getConnection(PlatformDatasource datasource) throws SQLException {
        HikariDataSource pool;
        synchronized (pools) {
            String key = poolKey(datasource);
            pool = pools.get(key);
            if (pool == null || pool.isClosed()) {
                pool = createPool(datasource, key);
                pools.put(key, pool);
                evictOldestPool();
            }
        }
        return pool.getConnection();
    }

    private HikariDataSource createPool(PlatformDatasource datasource, String key) {
        hostSafetyPolicy.validate(datasource.getHost());
        String password = credentialCipher.decrypt(datasource.getPassword());
        if (password == null) throw new ServiceException("数据库密码尚未配置");

        HikariConfig config = new HikariConfig();
        config.setPoolName("workflow-db-" + Integer.toUnsignedString(key.hashCode()));
        config.setJdbcUrl(jdbcUrlFactory.build(datasource));
        config.setUsername(datasource.getUsername());
        config.setPassword(password);
        config.setReadOnly(true);
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(3);
        config.setConnectionTimeout(Math.max(1000L,
                (long) defaultValue(datasource.getConnectTimeoutSeconds(), 5) * 1000L));
        config.setValidationTimeout(1000L);
        config.setIdleTimeout(60_000L);
        config.setMaxLifetime(10 * 60_000L);
        config.setInitializationFailTimeout(-1L);
        return new HikariDataSource(config);
    }

    private void evictOldestPool() {
        if (pools.size() <= MAX_POOLS) return;
        String oldestKey = pools.keySet().iterator().next();
        HikariDataSource oldest = pools.remove(oldestKey);
        if (oldest != null) oldest.close();
    }

    private String poolKey(PlatformDatasource datasource) {
        return datasource.getId() + ":" + datasource.getConfigVersion() + ":"
                + datasource.getCurrentVersionId();
    }

    private int defaultValue(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    @PreDestroy
    public void close() {
        synchronized (pools) {
            pools.values().forEach(HikariDataSource::close);
            pools.clear();
        }
    }
}
