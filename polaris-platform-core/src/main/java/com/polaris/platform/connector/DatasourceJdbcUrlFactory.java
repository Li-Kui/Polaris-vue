package com.polaris.platform.connector;

import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.PlatformDatasource;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 根据结构化字段生成受控 JDBC 地址。 */
@Component
public class DatasourceJdbcUrlFactory {

    private static final Set<String> TYPES = Set.of("MYSQL", "POSTGRESQL", "SQLSERVER");
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9._:-]{1,255}$");
    private static final Pattern DATABASE = Pattern.compile("^[A-Za-z0-9_$.-]{1,128}$");

    public String build(PlatformDatasource datasource) {
        String type = normalize(datasource.getDsType());
        String host = trim(datasource.getHost());
        String databaseName = trim(datasource.getDatabaseName());
        if (!TYPES.contains(type)) {
            throw new ServiceException("数据库类型仅支持 MYSQL、POSTGRESQL 或 SQLSERVER");
        }
        if (host == null || !HOST.matcher(host).matches()) {
            throw new ServiceException("数据库主机格式无效");
        }
        if (databaseName == null || !DATABASE.matcher(databaseName).matches()) {
            throw new ServiceException("数据库名称格式无效");
        }
        int port = datasource.getPort() == null ? defaultPort(type) : datasource.getPort();
        if (port < 1 || port > 65535) {
            throw new ServiceException("数据库端口必须在 1 到 65535 之间");
        }
        int connectTimeout = bounded(datasource.getConnectTimeoutSeconds(), 5, 1, 30);
        int queryTimeout = bounded(datasource.getQueryTimeoutSeconds(), 10, 1, 30);
        boolean ssl = Boolean.TRUE.equals(datasource.getSslEnabled());
        return switch (type) {
            case "MYSQL" -> "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                    + "?useSSL=" + ssl + "&requireSSL=" + ssl
                    + "&connectTimeout=" + connectTimeout * 1000
                    + "&socketTimeout=" + queryTimeout * 1000;
            case "POSTGRESQL" -> "jdbc:postgresql://" + host + ":" + port + "/" + databaseName
                    + "?sslmode=" + (ssl ? "require" : "disable")
                    + "&connectTimeout=" + connectTimeout + "&socketTimeout=" + queryTimeout;
            case "SQLSERVER" -> "jdbc:sqlserver://" + host + ":" + port
                    + ";databaseName=" + databaseName + ";encrypt=" + ssl
                    + ";loginTimeout=" + connectTimeout + ";socketTimeout=" + queryTimeout * 1000;
            default -> throw new ServiceException("数据库类型不受支持");
        };
    }

    public int defaultPort(String value) {
        return switch (normalize(value)) {
            case "MYSQL" -> 3306;
            case "POSTGRESQL" -> 5432;
            case "SQLSERVER" -> 1433;
            default -> 0;
        };
    }

    private int bounded(Integer value, int defaultValue, int minimum, int maximum) {
        int result = value == null ? defaultValue : value;
        if (result < minimum || result > maximum) {
            throw new ServiceException("连接和查询超时时间必须在 1 到 30 秒之间");
        }
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
