package com.polaris.platform.tenant;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * MyBatis 多租户 SQL 拦截器。
 * 在中台模式下自动为目标表追加 tenant_id 条件。
 */
@Slf4j
public class TenantSqlInterceptor implements Interceptor {

    /** 需要追加租户隔离的表名 */
    private static final Set<String> TENANT_TABLES = Set.of(
        "ai_model_config", "ai_knowledge_base", "ai_conversation",
        "ai_agent", "platform_api_key", "platform_datasource",
        "platform_api_connector"
    );

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        CallerContext ctx = CallerContextHolder.get();

        // 非中台模式 或 超管 → 不追加租户条件
        if (ctx == null || ctx.getTenantId() == null || ctx.isSuperAdmin()) {
            return invocation.proceed();
        }

        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        BoundSql boundSql = ms.getBoundSql(parameter);
        String originalSql = boundSql.getSql();

        // 检查 SQL 是否涉及租户表
        boolean needRewrite = TENANT_TABLES.stream()
                .anyMatch(table -> originalSql.toLowerCase().contains(table));

        if (!needRewrite) {
            return invocation.proceed();
        }

        // 简易追加 tenant_id 条件
        String tenantId = ctx.getTenantId();
        String modifiedSql = addTenantCondition(originalSql, tenantId);

        // 替换 SQL
        setFieldValue(boundSql, "sql", modifiedSql);

        // 重建 MappedStatement
        MappedStatement newMs = rebuildMappedStatement(ms, boundSql);
        invocation.getArgs()[0] = newMs;

        return invocation.proceed();
    }

    /**
     * 为 SQL 追加 tenant_id 条件（简化实现）
     */
    private String addTenantCondition(String sql, String tenantId) {
        String lowerSql = sql.toLowerCase().trim();

        if (lowerSql.startsWith("select") || lowerSql.startsWith("delete")) {
            if (lowerSql.contains("where")) {
                return sql + " AND tenant_id = " + tenantId;
            } else {
                // 在 ORDER BY / GROUP BY / LIMIT 前插入 WHERE
                String insertPoint = findInsertPoint(sql);
                return insertPoint != null
                    ? sql.substring(0, sql.toLowerCase().indexOf(insertPoint))
                        + " WHERE tenant_id = " + tenantId + " "
                        + sql.substring(sql.toLowerCase().indexOf(insertPoint))
                    : sql + " WHERE tenant_id = " + tenantId;
            }
        } else if (lowerSql.startsWith("update")) {
            if (lowerSql.contains("where")) {
                return sql + " AND tenant_id = " + tenantId;
            } else {
                return sql + " WHERE tenant_id = " + tenantId;
            }
        }

        return sql;
    }

    private String findInsertPoint(String sql) {
        String lower = sql.toLowerCase();
        for (String keyword : new String[]{"order by", "group by", "limit", "having"}) {
            if (lower.contains(keyword)) return keyword;
        }
        return null;
    }

    private MappedStatement rebuildMappedStatement(MappedStatement ms, BoundSql boundSql) {
        SqlSource newSqlSource = (p) -> boundSql;
        return new MappedStatement.Builder(ms.getConfiguration(), ms.getId(), newSqlSource, ms.getSqlCommandType())
                .resource(ms.getResource())
                .parameterMap(ms.getParameterMap())
                .resultMaps(ms.getResultMaps())
                .fetchSize(ms.getFetchSize())
                .timeout(ms.getTimeout())
                .statementType(ms.getStatementType())
                .resultSetType(ms.getResultSetType())
                .cache(ms.getCache())
                .flushCacheRequired(ms.isFlushCacheRequired())
                .useCache(ms.isUseCache())
                .keyGenerator(ms.getKeyGenerator())
                .build();
    }

    private void setFieldValue(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            log.warn("反射设置字段失败: {}", e.getMessage());
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
