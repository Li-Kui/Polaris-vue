-- 工作流动态 Schema：API 连接器响应体契约。
-- 现有环境执行一次；全新环境已由 ry_platform.sql 创建该字段。
ALTER TABLE `platform_api_connector`
    ADD COLUMN `response_schema` JSON DEFAULT NULL COMMENT '响应体 JSON Schema'
    AFTER `default_headers`;

-- 单节点隔离试运行异步任务和脱敏结果记录。
-- 当前功能尚未投产且无需保留开发期试运行数据，直接重建以补齐租约字段。
DROP TABLE IF EXISTS `ai_workflow_node_test_audit`;
DROP TABLE IF EXISTS `ai_workflow_node_test_run`;
CREATE TABLE IF NOT EXISTS `ai_workflow_node_test_run` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '试运行记录主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `test_run_id` varchar(64) NOT NULL COMMENT '公开试运行任务ID',
  `definition_id` bigint(20) NOT NULL COMMENT '工作流定义ID',
  `draft_revision` bigint(20) NOT NULL COMMENT '草稿修订快照',
  `node_id` varchar(128) NOT NULL COMMENT '定义中的节点ID',
  `node_type` varchar(64) NOT NULL COMMENT '节点类型快照',
  `handler_version` varchar(32) NOT NULL COMMENT '处理器版本快照',
  `environment` varchar(16) NOT NULL COMMENT '资源环境：DEV开发、TEST测试',
  `test_mode` varchar(32) NOT NULL DEFAULT 'NODE' COMMENT '试运行模式：NODE单节点、UPSTREAM_CHAIN线性上游链',
  `status` varchar(16) NOT NULL COMMENT '任务状态',
  `side_effect` varchar(16) NOT NULL COMMENT '副作用类型',
  `principal_type` varchar(32) NOT NULL COMMENT '调用主体类型',
  `principal_id` varchar(128) NOT NULL COMMENT '调用主体ID',
  `input_json` longtext NOT NULL COMMENT '脱敏后的节点输入JSON',
  `payload_ciphertext` longtext DEFAULT NULL COMMENT '仅在任务结束前保留的加密执行输入',
  `output_json` longtext DEFAULT NULL COMMENT '脱敏后的节点输出JSON',
  `usage_json` text DEFAULT NULL COMMENT '不含密钥的用量JSON',
  `schema_source` varchar(64) DEFAULT NULL COMMENT '有效Schema来源',
  `schema_source_version` varchar(128) DEFAULT NULL COMMENT '有效Schema来源版本',
  `schema_diagnostics_json` text DEFAULT NULL COMMENT 'Schema诊断JSON',
  `node_config_hash` char(64) NOT NULL COMMENT '节点类型、配置和资源引用摘要',
  `timeout_seconds` int(11) NOT NULL COMMENT '任务超时秒数',
  `cancel_requested` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否请求取消',
  `runner_id` varchar(128) DEFAULT NULL COMMENT '当前租约持有实例',
  `lease_until` datetime DEFAULT NULL COMMENT '租约到期时间',
  `fencing_token` bigint(20) NOT NULL DEFAULT '0' COMMENT '隔离令牌',
  `attempt_count` int(11) NOT NULL DEFAULT '0' COMMENT '任务领取次数',
  `error_code` varchar(64) DEFAULT NULL COMMENT '稳定错误码',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '脱敏错误信息',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '实际执行耗时毫秒',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `heartbeat_time` datetime DEFAULT NULL COMMENT '最近租约心跳时间',
  `finish_time` datetime DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_node_test_run_id` (`test_run_id`),
  KEY `idx_wf_node_test_definition` (`tenant_id`, `definition_id`, `create_time`),
  KEY `idx_wf_node_test_status` (`status`, `lease_until`, `create_time`),
  KEY `idx_wf_node_test_samples` (`definition_id`, `node_id`, `environment`, `principal_type`, `principal_id`, `node_config_hash`, `schema_source_version`, `status`, `finish_time`),
  CONSTRAINT `fk_wf_node_test_definition` FOREIGN KEY (`definition_id`)
    REFERENCES `ai_workflow_definition` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_wf_node_test_environment` CHECK (`environment` IN ('DEV','TEST')),
  CONSTRAINT `chk_wf_node_test_mode` CHECK (`test_mode` IN ('NODE','UPSTREAM_CHAIN')),
  CONSTRAINT `chk_wf_node_test_status` CHECK (`status` IN (
    'QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED'
  )),
  CONSTRAINT `chk_wf_node_test_side_effect` CHECK (`side_effect` IN ('NONE','READ'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单节点隔离试运行记录';

-- 单节点试运行独立审计与费用摘要，不保存业务输入或输出。
CREATE TABLE IF NOT EXISTS `ai_workflow_node_test_audit` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审计摘要主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `test_run_id` varchar(64) NOT NULL COMMENT '公开试运行任务ID',
  `definition_id` bigint(20) NOT NULL COMMENT '工作流定义ID',
  `draft_revision` bigint(20) NOT NULL COMMENT '草稿修订快照',
  `node_id` varchar(128) NOT NULL COMMENT '定义中的节点ID',
  `node_type` varchar(64) NOT NULL COMMENT '节点类型快照',
  `handler_version` varchar(32) NOT NULL COMMENT '处理器版本快照',
  `environment` varchar(16) NOT NULL COMMENT '资源环境',
  `test_mode` varchar(32) NOT NULL DEFAULT 'NODE' COMMENT '试运行模式',
  `side_effect` varchar(16) NOT NULL COMMENT '副作用类型',
  `principal_type` varchar(32) NOT NULL COMMENT '调用主体类型',
  `principal_id` varchar(128) NOT NULL COMMENT '调用主体ID',
  `node_config_hash` char(64) NOT NULL COMMENT '节点配置摘要',
  `schema_source` varchar(64) DEFAULT NULL COMMENT '有效Schema来源',
  `schema_source_version` varchar(128) DEFAULT NULL COMMENT '有效Schema来源版本',
  `outcome_status` varchar(16) DEFAULT NULL COMMENT '最终状态',
  `usage_json` text DEFAULT NULL COMMENT '仅包含数值的用量摘要',
  `total_tokens` bigint(20) DEFAULT NULL COMMENT '归一化Token总量',
  `cost_amount` decimal(20,8) DEFAULT NULL COMMENT '节点上报的费用数值',
  `attempt_count` int(11) DEFAULT NULL COMMENT '最终领取次数',
  `error_code` varchar(64) DEFAULT NULL COMMENT '稳定错误码',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '实际执行耗时毫秒',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `finish_time` datetime DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_node_test_audit_run` (`test_run_id`),
  KEY `idx_wf_node_test_audit_tenant` (`tenant_id`, `create_time`),
  KEY `idx_wf_node_test_audit_principal` (`tenant_id`, `principal_type`, `principal_id`, `create_time`),
  CONSTRAINT `chk_wf_node_test_audit_mode` CHECK (`test_mode` IN ('NODE','UPSTREAM_CHAIN')),
  CONSTRAINT `chk_wf_node_test_audit_status` CHECK (`outcome_status` IS NULL OR `outcome_status` IN (
    'SUCCEEDED','FAILED','CANCELLED'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单节点试运行审计与费用摘要';
