-- =============================================================================
-- Polaris Model Center V2.4 - 合并DDL变更
--
-- 仅包含Schema变更（字段新增/修改/删除、建表、加索引），不含任何数据迁移。
-- 适用于在 sql/ry_ai.sql 基础表之上执行。可直接删除重录。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. ai_model_config: 新增V2字段
-- -----------------------------------------------------------------------------
ALTER TABLE `ai_model_config`
    ADD COLUMN `description` varchar(500) DEFAULT NULL
        COMMENT '模型备注，不参与能力路由' AFTER `model_type`,
    ADD COLUMN `model_code` varchar(100) NOT NULL
        COMMENT 'Polaris稳定逻辑模型编码',
    ADD COLUMN `connection_id` bigint NOT NULL
        COMMENT 'Provider Connection ID',
    ADD COLUMN `revision` bigint NOT NULL DEFAULT 1
        COMMENT '运行配置修订号',
    ADD COLUMN `tenant_scope_key` varchar(32)
        GENERATED ALWAYS AS (
            CASE
                WHEN `tenant_id` IS NULL THEN 'GLOBAL'
                ELSE CONCAT('T:', CAST(`tenant_id` AS CHAR))
            END
        ) STORED
        COMMENT '仅用于model_code唯一索引，不参与权限判断';

-- -----------------------------------------------------------------------------
-- 2. ai_model_config: 删除已废弃的旧字段
-- -----------------------------------------------------------------------------
ALTER TABLE `ai_model_config`
    DROP COLUMN `provider`,
    DROP COLUMN `api_key`,
    DROP COLUMN `base_url`,
    DROP COLUMN `embedding_dimension`,
    DROP COLUMN `embedding_dimension_mode`,
    DROP COLUMN `embedding_max_input_tokens`,
    DROP COLUMN `embedding_batch_size`,
    DROP COLUMN `max_tokens`,
    DROP COLUMN `temperature`,
    DROP COLUMN `max_history_messages`,
    DROP COLUMN `system_prompt`,
    DROP COLUMN `is_default`,
    DROP COLUMN `enable_thinking`,
    DROP COLUMN `reasoning_effort`,
    DROP COLUMN `enable_search`,
    DROP COLUMN `search_key`,
    DROP COLUMN `image_capabilities`,
    DROP COLUMN `model_features`,
    DROP COLUMN `default_image_size`,
    DROP COLUMN `model_description`,
    DROP COLUMN `access_mode`,
    DROP COLUMN `max_concurrency`,
    DROP COLUMN `enabled_tools`;

-- -----------------------------------------------------------------------------
-- 3. ai_model_config: 新增索引
-- -----------------------------------------------------------------------------
ALTER TABLE `ai_model_config`
    ADD UNIQUE KEY `uk_model_code` (`tenant_scope_key`, `model_code`),
    ADD KEY `idx_model_connection` (`connection_id`),
    ADD KEY `idx_model_scope_status` (`tenant_id`, `dept_id`, `status`, `del_flag`);

-- -----------------------------------------------------------------------------
-- 4. ai_knowledge_base: 新增V2快照字段
-- -----------------------------------------------------------------------------
ALTER TABLE `ai_knowledge_base`
    ADD COLUMN `embedding_dimension` int DEFAULT NULL
        COMMENT '索引绑定的实际向量维度快照' AFTER `embedding_model_id`,
    ADD COLUMN `embedding_model_revision` bigint DEFAULT NULL
        COMMENT '索引绑定的模型修订号快照' AFTER `embedding_dimension`,
    ADD COLUMN `embedding_schema_hash` char(64) DEFAULT NULL
        COMMENT '索引绑定的TEXT_EMBEDDING Schema Hash' AFTER `embedding_model_revision`;

-- -----------------------------------------------------------------------------
-- 5. ai_image_task: 新增V2运行快照字段
-- -----------------------------------------------------------------------------
ALTER TABLE `ai_image_task`
    ADD COLUMN `model_revision` bigint DEFAULT NULL
        COMMENT '任务创建时模型修订号' AFTER `model_config_id`,
    ADD COLUMN `connection_id` bigint DEFAULT NULL
        COMMENT '任务创建时Provider Connection ID' AFTER `model_revision`,
    ADD COLUMN `connection_revision` bigint DEFAULT NULL
        COMMENT '任务创建时Connection修订号' AFTER `connection_id`,
    ADD COLUMN `capability_code` varchar(64) DEFAULT NULL
        COMMENT '实际Invocation Capability' AFTER `connection_revision`,
    ADD COLUMN `runtime_snapshot` json DEFAULT NULL
        COMMENT '不含Credential的Model Runtime Snapshot' AFTER `capability_code`,
    ADD COLUMN `schema_hash` char(64) DEFAULT NULL
        COMMENT 'Invocation Runtime Schema Hash' AFTER `runtime_snapshot`;

-- -----------------------------------------------------------------------------
-- 6. 新建表: ai_provider_connection
-- -----------------------------------------------------------------------------
CREATE TABLE `ai_provider_connection` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint DEFAULT NULL,
    `dept_id` bigint DEFAULT NULL,
    `connection_name` varchar(100) NOT NULL,
    `provider_code` varchar(50) NOT NULL,
    `protocol_code` varchar(50) NOT NULL,
    `network_mode` varchar(20) NOT NULL DEFAULT 'PUBLIC',
    `base_url` varchar(500) DEFAULT NULL,
    `credential_ciphertext` text DEFAULT NULL,
    `extra_config` json DEFAULT NULL,
    `revision` bigint NOT NULL DEFAULT 1,
    `status` char(1) NOT NULL DEFAULT '1',
    `del_flag` char(1) NOT NULL DEFAULT '0',
    `create_by` varchar(64) DEFAULT '',
    `create_time` datetime DEFAULT NULL,
    `update_by` varchar(64) DEFAULT '',
    `update_time` datetime DEFAULT NULL,
    `remark` varchar(500) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_provider_scope` (`tenant_id`, `dept_id`, `status`, `del_flag`),
    KEY `idx_provider_protocol` (`protocol_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Provider连接配置';

-- -----------------------------------------------------------------------------
-- 7. 新建表: ai_model_capability
-- -----------------------------------------------------------------------------
CREATE TABLE `ai_model_capability` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `model_config_id` bigint NOT NULL,
    `capability_code` varchar(64) NOT NULL,
    `applies_to_capability_code` varchar(64) NOT NULL DEFAULT '',
    `schema_version` int NOT NULL DEFAULT 1,
    `schema_hash` char(64) DEFAULT NULL,
    `config_json` json DEFAULT NULL,
    `enabled` char(1) NOT NULL DEFAULT '1',
    `capability_source` varchar(24) NOT NULL DEFAULT 'MANUAL'
        COMMENT 'REMOTE_PROVIDER/MODEL_PROFILE/PROVIDER_PROFILE/PROTOCOL_DEFAULT/INFERRED/MANUAL',
    `create_time` datetime DEFAULT NULL,
    `update_time` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_capability`
        (`model_config_id`, `capability_code`, `applies_to_capability_code`),
    KEY `idx_capability_route` (`capability_code`, `enabled`, `model_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型能力配置';

-- -----------------------------------------------------------------------------
-- 8. 新建表: ai_model_runtime_policy
-- -----------------------------------------------------------------------------
CREATE TABLE `ai_model_runtime_policy` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `model_config_id` bigint NOT NULL,
    `capability_code` varchar(64) NOT NULL DEFAULT '',
    `max_concurrency` int DEFAULT NULL,
    `connect_timeout_ms` int DEFAULT NULL,
    `read_timeout_ms` int DEFAULT NULL,
    `retry_count` int DEFAULT NULL,
    `qps_limit` decimal(12,4) DEFAULT NULL,
    `priority` int DEFAULT NULL,
    `extra_config` json DEFAULT NULL,
    `create_time` datetime DEFAULT NULL,
    `update_time` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_runtime` (`model_config_id`, `capability_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型运行策略';

-- -----------------------------------------------------------------------------
-- 9. 新建表: ai_model_default
-- -----------------------------------------------------------------------------
CREATE TABLE `ai_model_default` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `scope_type` varchar(20) NOT NULL,
    `scope_id` bigint NOT NULL DEFAULT 0,
    `capability_code` varchar(64) NOT NULL,
    `model_config_id` bigint NOT NULL,
    `create_time` datetime DEFAULT NULL,
    `update_time` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_default_scope_capability`
        (`scope_type`, `scope_id`, `capability_code`),
    KEY `idx_default_model` (`model_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI默认模型';
