-- 工作流所有者作用域调整脚本（2026-08-25）。
-- 管理端工作流归属系统，租户端工作流自动归属当前租户，任何页面都不再选择租户。
-- 资源绑定数据允许清理，本脚本会重建资源绑定表，执行前无需迁移原绑定记录。

SET FOREIGN_KEY_CHECKS = 0;
SET @workflow_scope_schema_name = DATABASE();

-- 删除旧的工作流定义归属约束，以便统一系统归属名称。
SET @workflow_scope_sql = (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `ai_workflow_definition` DROP CHECK `chk_wf_definition_owner`',
    'SELECT ''工作流定义归属约束不存在，跳过''')
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = @workflow_scope_schema_name
    AND TABLE_NAME = 'ai_workflow_definition'
    AND CONSTRAINT_NAME = 'chk_wf_definition_owner'
);
PREPARE workflow_scope_stmt FROM @workflow_scope_sql;
EXECUTE workflow_scope_stmt;
DEALLOCATE PREPARE workflow_scope_stmt;

-- 管理端工作流统一改为系统归属。
UPDATE `ai_workflow_definition`
SET `owner_type` = 'SYSTEM',
    `owner_id` = 0,
    `tenant_id` = NULL
WHERE `tenant_id` IS NULL;

-- 恢复工作流定义归属约束。
ALTER TABLE `ai_workflow_definition`
  ADD CONSTRAINT `chk_wf_definition_owner` CHECK (
    (`owner_type` = 'SYSTEM' AND `owner_id` = 0 AND `tenant_id` IS NULL)
    OR (`owner_type` = 'TENANT' AND `owner_id` = `tenant_id` AND `tenant_id` IS NOT NULL)
  );

-- 原资源绑定表只支持租户作用域，直接清理后按所有者作用域重建。
DROP TABLE IF EXISTS `ai_workflow_resource_binding`;

CREATE TABLE `ai_workflow_resource_binding` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '资源绑定主键',
  `owner_type` varchar(32) NOT NULL COMMENT '归属类型：SYSTEM系统、TENANT租户',
  `owner_id` bigint(20) NOT NULL COMMENT '归属标识，系统为0，租户为租户标识',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户标识，系统资源绑定为空',
  `environment` varchar(16) NOT NULL COMMENT '资源环境：DEV开发、TEST测试、PROD生产',
  `resource_kind` varchar(64) NOT NULL COMMENT '资源类型：MODEL模型、AGENT智能体、KNOWLEDGE_BASE知识库、API_CONNECTOR接口连接器、DATASOURCE数据源',
  `resource_key` varchar(128) NOT NULL COMMENT '定义中的逻辑资源标识',
  `resource_id` varchar(128) NOT NULL COMMENT '实际资源标识',
  `binding_version` int(11) NOT NULL DEFAULT '1' COMMENT '单调递增的绑定版本号',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE启用、DISABLED停用',
  `lock_version` int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_binding_key` (`owner_type`, `owner_id`, `environment`, `resource_kind`, `resource_key`),
  KEY `idx_wf_binding_resource` (`owner_type`, `owner_id`, `resource_kind`, `resource_id`),
  CONSTRAINT `chk_wf_binding_owner` CHECK (
    (`owner_type` = 'SYSTEM' AND `owner_id` = 0 AND `tenant_id` IS NULL)
    OR (`owner_type` = 'TENANT' AND `owner_id` = `tenant_id` AND `tenant_id` IS NOT NULL)
  ),
  CONSTRAINT `chk_wf_binding_environment` CHECK (`environment` IN ('DEV','TEST','PROD')),
  CONSTRAINT `chk_wf_binding_status` CHECK (`status` IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流资源绑定';

SET @workflow_scope_sql = NULL;
SET @workflow_scope_schema_name = NULL;
SET FOREIGN_KEY_CHECKS = 1;
