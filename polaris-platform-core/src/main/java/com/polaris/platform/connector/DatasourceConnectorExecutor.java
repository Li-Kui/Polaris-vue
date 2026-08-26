package com.polaris.platform.connector;

import com.polaris.platform.domain.PlatformDatasource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部数据源执行器（支持 MySQL / PostgreSQL / SQL Server 测试与查询）
 */
@Slf4j
@Component
public class DatasourceConnectorExecutor {

    private final ReadOnlySqlPolicy sqlPolicy;

    public DatasourceConnectorExecutor(ReadOnlySqlPolicy sqlPolicy) {
        this.sqlPolicy = sqlPolicy;
    }

    public boolean testConnection(PlatformDatasource ds) {
        try (Connection conn = getConnection(ds)) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            log.error("测试数据源连接失败: {}", ds.getDsName(), e);
            throw new RuntimeException("连接失败: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> executeQuery(PlatformDatasource ds, String sql, int maxRows) {
        String safeSql = sqlPolicy.validate(sql);
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = getConnection(ds)) {
            conn.setReadOnly(true);
            try (Statement stmt = conn.createStatement()) {
                if (ds.getQueryTimeout() != null && ds.getQueryTimeout() > 0) {
                    stmt.setQueryTimeout(Math.min(ds.getQueryTimeout(), 30));
                }
                stmt.setMaxRows(maxRows > 0 ? Math.min(maxRows, 1000) : 100);
                try (ResultSet rs = stmt.executeQuery(safeSql)) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int colCount = metaData.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(metaData.getColumnLabel(i), rs.getObject(i));
                        }
                        result.add(row);
                    }
                }
            }
        } catch (Exception e) {
            log.error("执行数据源只读查询失败, ds={}", ds.getDsName(), e);
            throw new RuntimeException("查询执行异常: " + e.getMessage());
        }
        return result;
    }

    private Connection getConnection(PlatformDatasource ds) throws SQLException {
        return DriverManager.getConnection(ds.getJdbcUrl(), ds.getUsername(), ds.getPassword());
    }
}
