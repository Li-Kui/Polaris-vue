-- 工作流现有人工智能资源接入增量升级脚本（2026-08-25）。
-- 适用于已经存在模型、智能体和知识库数据，但尚未补齐工作流资源字段的数据库。
-- 现有数据默认作为管理端共享资源，不删除、不覆盖业务配置，可重复执行。

SET @workflow_resource_schema_name = DATABASE();

-- 为模型配置增加租户归属字段，原有数据保持为空并作为共享资源。
SET @workflow_resource_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_model_config` ADD COLUMN `tenant_id` bigint DEFAULT NULL COMMENT ''所属租户标识，管理端共享资源为空'' AFTER `id`',
    'SELECT ''ai_model_config.tenant_id已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @workflow_resource_schema_name
    AND TABLE_NAME = 'ai_model_config'
    AND COLUMN_NAME = 'tenant_id'
);
PREPARE workflow_resource_upgrade_stmt FROM @workflow_resource_upgrade_sql;
EXECUTE workflow_resource_upgrade_stmt;
DEALLOCATE PREPARE workflow_resource_upgrade_stmt;

-- 为模型配置增加用途类型字段，原有模型默认作为聊天模型。
SET @workflow_resource_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_model_config` ADD COLUMN `model_type` varchar(32) NOT NULL DEFAULT ''CHAT'' COMMENT ''模型用途类型：聊天、向量、图像'' AFTER `base_url`',
    'SELECT ''ai_model_config.model_type已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @workflow_resource_schema_name
    AND TABLE_NAME = 'ai_model_config'
    AND COLUMN_NAME = 'model_type'
);
PREPARE workflow_resource_upgrade_stmt FROM @workflow_resource_upgrade_sql;
EXECUTE workflow_resource_upgrade_stmt;
DEALLOCATE PREPARE workflow_resource_upgrade_stmt;

-- 为知识库增加租户归属字段，原有数据保持为空并作为共享资源。
SET @workflow_resource_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_knowledge_base` ADD COLUMN `tenant_id` bigint DEFAULT NULL COMMENT ''所属租户标识，管理端共享资源为空'' AFTER `id`',
    'SELECT ''ai_knowledge_base.tenant_id已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @workflow_resource_schema_name
    AND TABLE_NAME = 'ai_knowledge_base'
    AND COLUMN_NAME = 'tenant_id'
);
PREPARE workflow_resource_upgrade_stmt FROM @workflow_resource_upgrade_sql;
EXECUTE workflow_resource_upgrade_stmt;
DEALLOCATE PREPARE workflow_resource_upgrade_stmt;

-- 为智能体增加租户归属字段，原有数据保持为空并作为共享资源。
SET @workflow_resource_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_agent` ADD COLUMN `tenant_id` bigint DEFAULT NULL COMMENT ''所属租户标识，管理端共享资源为空'' AFTER `id`',
    'SELECT ''ai_agent.tenant_id已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @workflow_resource_schema_name
    AND TABLE_NAME = 'ai_agent'
    AND COLUMN_NAME = 'tenant_id'
);
PREPARE workflow_resource_upgrade_stmt FROM @workflow_resource_upgrade_sql;
EXECUTE workflow_resource_upgrade_stmt;
DEALLOCATE PREPARE workflow_resource_upgrade_stmt;

-- 为智能体增加模型配置关联字段。
SET @workflow_resource_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_agent` ADD COLUMN `model_config_id` bigint DEFAULT NULL COMMENT ''关联的模型配置标识'' AFTER `model_name`',
    'SELECT ''ai_agent.model_config_id已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @workflow_resource_schema_name
    AND TABLE_NAME = 'ai_agent'
    AND COLUMN_NAME = 'model_config_id'
);
PREPARE workflow_resource_upgrade_stmt FROM @workflow_resource_upgrade_sql;
EXECUTE workflow_resource_upgrade_stmt;
DEALLOCATE PREPARE workflow_resource_upgrade_stmt;

-- 优先按模型名称匹配智能体底座模型，无法匹配时使用默认聊天模型。
UPDATE `ai_agent` AS agent
SET `model_config_id` = (
  SELECT model.`id`
  FROM `ai_model_config` model
  WHERE model.`del_flag` = '0'
    AND model.`status` = '1'
    AND model.`model_type` = 'CHAT'
  ORDER BY CASE WHEN model.`model_name` = agent.`model_name` THEN 0 ELSE 1 END,
    CASE WHEN model.`is_default` = '1' THEN 0 ELSE 1 END,
    model.`id`
  LIMIT 1
)
WHERE `model_config_id` IS NULL
  AND `del_flag` = '0';

-- 增加工作流模型资源查询索引。
SET @workflow_resource_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_model_config` ADD INDEX `idx_ai_model_workflow_resource` (`tenant_id`, `del_flag`, `status`, `model_type`)',
    'SELECT ''ai_model_config.idx_ai_model_workflow_resource已存在，跳过''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @workflow_resource_schema_name
    AND TABLE_NAME = 'ai_model_config'
    AND INDEX_NAME = 'idx_ai_model_workflow_resource'
);
PREPARE workflow_resource_upgrade_stmt FROM @workflow_resource_upgrade_sql;
EXECUTE workflow_resource_upgrade_stmt;
DEALLOCATE PREPARE workflow_resource_upgrade_stmt;

-- 增加工作流知识库资源查询索引。
SET @workflow_resource_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_knowledge_base` ADD INDEX `idx_ai_knowledge_workflow_resource` (`tenant_id`, `del_flag`)',
    'SELECT ''ai_knowledge_base.idx_ai_knowledge_workflow_resource已存在，跳过''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @workflow_resource_schema_name
    AND TABLE_NAME = 'ai_knowledge_base'
    AND INDEX_NAME = 'idx_ai_knowledge_workflow_resource'
);
PREPARE workflow_resource_upgrade_stmt FROM @workflow_resource_upgrade_sql;
EXECUTE workflow_resource_upgrade_stmt;
DEALLOCATE PREPARE workflow_resource_upgrade_stmt;

-- 增加工作流智能体资源查询索引。
SET @workflow_resource_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_agent` ADD INDEX `idx_ai_agent_workflow_resource` (`tenant_id`, `del_flag`, `status`)',
    'SELECT ''ai_agent.idx_ai_agent_workflow_resource已存在，跳过''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @workflow_resource_schema_name
    AND TABLE_NAME = 'ai_agent'
    AND INDEX_NAME = 'idx_ai_agent_workflow_resource'
);
PREPARE workflow_resource_upgrade_stmt FROM @workflow_resource_upgrade_sql;
EXECUTE workflow_resource_upgrade_stmt;
DEALLOCATE PREPARE workflow_resource_upgrade_stmt;

SET @workflow_resource_upgrade_sql = NULL;
SET @workflow_resource_schema_name = NULL;
