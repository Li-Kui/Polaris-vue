package com.polaris.framework.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.polaris.ai.core.context.CallerUtils;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.Locale;
import java.util.Set;

/**
 * 中台模式租户行级隔离处理器。
 */
public class PlatformTenantLineHandler implements TenantLineHandler {

    /** 需要强制租户隔离的数据表（严格租户隔离，各租户数据完全独立） */
    private static final Set<String> TENANT_TABLES = Set.of(
        "ai_model_config", "ai_knowledge_base", "ai_agent", "ai_conversation",
        "platform_api_key", "platform_datasource", "platform_datasource_version",
        "platform_api_connector",
        "platform_tenant_user",
        "ai_workflow_definition", "ai_workflow_version",
        "ai_workflow_resource_binding", "ai_workflow_execution",
        "ai_workflow_node_run", "ai_workflow_checkpoint",
        "ai_workflow_event", "ai_workflow_approval_instance",
        "ai_workflow_approval_stage", "ai_workflow_approval_assignment",
        "ai_workflow_approval_decision",
        "ai_workflow_artifact", "ai_workflow_trigger",
        "ai_workflow_outbox", "ai_workflow_concurrency_quota"
    );

    @Override
    public Expression getTenantId() {
        String tenantId = CallerUtils.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("中台模式缺少租户ID");
        }
        try {
            long value = Long.parseLong(tenantId);
            if (value <= 0) {
                throw new IllegalStateException("中台租户ID格式错误");
            }
            return new LongValue(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("中台租户ID格式错误");
        }
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // 非中台模式 或 超级管理员 → 完全不增加租户限制（管理后台可看全部数据）
        if (!CallerUtils.isPlatformMode() || CallerUtils.isSuperAdmin()) {
            return true;
        }
        // 仅对多租户表生效
        return tableName == null || !TENANT_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
    }
}
