-- ----------------------------------------------------------------------------
-- Polaris AI 工作流引擎完整数据库建表与初始化脚本
-- ----------------------------------------------------------------------------

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 历史工作流数据及旧表清理
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_graph_checkpoint`;
DROP TABLE IF EXISTS `ai_workflow_approval`;
DROP TABLE IF EXISTS `ai_workflow_outbox`;
DROP TABLE IF EXISTS `ai_workflow_trigger`;
DROP TABLE IF EXISTS `ai_workflow_artifact`;
DROP TABLE IF EXISTS `ai_workflow_approval_decision`;
DROP TABLE IF EXISTS `ai_workflow_approval_assignment`;
DROP TABLE IF EXISTS `ai_workflow_approval_stage`;
DROP TABLE IF EXISTS `ai_workflow_approval_instance`;
DROP TABLE IF EXISTS `ai_workflow_event`;
DROP TABLE IF EXISTS `ai_workflow_checkpoint`;
DROP TABLE IF EXISTS `ai_workflow_node_run`;
DROP TABLE IF EXISTS `ai_workflow_node_test_audit`;
DROP TABLE IF EXISTS `ai_workflow_node_test_run`;
DROP TABLE IF EXISTS `ai_workflow_concurrency_quota`;
DROP TABLE IF EXISTS `ai_workflow_resource_binding`;
DROP TABLE IF EXISTS `ai_workflow_execution`;
DROP TABLE IF EXISTS `ai_workflow_version`;
DROP TABLE IF EXISTS `ai_workflow_definition`;
DROP TABLE IF EXISTS `ai_workflow`;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 一、工作流核心表结构
-- ============================================================================

-- ----------------------------
-- 1. Table structure for ai_workflow_definition (工作流定义及草稿)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_definition` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '定义主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户标识，系统工作流为空',
  `owner_type` varchar(32) NOT NULL COMMENT '归属类型：SYSTEM系统、TENANT租户',
  `owner_id` bigint(20) NOT NULL COMMENT '归属标识，系统工作流为0，租户工作流为租户标识',
  `workflow_code` varchar(64) NOT NULL COMMENT '工作流编码，在归属范围内唯一',
  `workflow_name` varchar(128) NOT NULL COMMENT '工作流名称',
  `description` varchar(2000) DEFAULT NULL COMMENT '工作流描述',
  `tags_json` text DEFAULT NULL COMMENT '标签列表JSON',
  `draft_schema_version` varchar(16) NOT NULL DEFAULT '2.0' COMMENT '草稿定义结构版本',
  `draft_json` longtext NOT NULL COMMENT '可修改的草稿定义JSON',
  `draft_revision` bigint(20) NOT NULL DEFAULT '1' COMMENT '草稿单调递增修订号',
  `current_published_version_id` varchar(64) DEFAULT NULL COMMENT '当前已发布的不可变版本ID',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿、ACTIVE启用、DISABLED停用',
  `lock_version` int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志：0存在、2删除',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_definition_owner_code` (`owner_type`, `owner_id`, `workflow_code`),
  KEY `idx_wf_definition_tenant_status` (`tenant_id`, `status`, `del_flag`),
  KEY `idx_wf_definition_current_version` (`current_published_version_id`),
  CONSTRAINT `chk_wf_definition_owner` CHECK (
    (`owner_type` = 'SYSTEM' AND `owner_id` = 0 AND `tenant_id` IS NULL)
    OR (`owner_type` = 'TENANT' AND `owner_id` = `tenant_id` AND `tenant_id` IS NOT NULL)
  ),
  CONSTRAINT `chk_wf_definition_status` CHECK (`status` IN ('DRAFT','ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义及可修改草稿';

-- ----------------------------
-- 2. Table structure for ai_workflow_version (工作流不可变发布版本)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_version` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '不可变版本主键',
  `version_id` varchar(64) NOT NULL COMMENT '服务端生成的公开不可变版本ID',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户标识，系统工作流为空',
  `definition_id` bigint(20) NOT NULL COMMENT '工作流定义ID',
  `version_no` int(11) NOT NULL COMMENT '业务发布版本号',
  `schema_version` varchar(16) NOT NULL COMMENT '工作流定义结构版本',
  `definition_json` longtext NOT NULL COMMENT '不可变工作流定义JSON',
  `plan_schema_version` varchar(16) NOT NULL COMMENT '执行计划结构版本',
  `execution_plan_json` longtext NOT NULL COMMENT '编译后的不可变执行计划JSON',
  `content_hash` char(64) NOT NULL COMMENT '规范化执行内容的SHA-256摘要',
  `status` varchar(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态：PUBLISHED已发布、RETIRED已退役',
  `published_by` varchar(64) NOT NULL COMMENT '发布者身份',
  `published_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_version_id` (`version_id`),
  UNIQUE KEY `uk_wf_version_no` (`definition_id`, `version_no`),
  UNIQUE KEY `uk_wf_version_hash` (`definition_id`, `content_hash`),
  KEY `idx_wf_version_tenant` (`tenant_id`, `status`, `published_time`),
  CONSTRAINT `fk_wf_version_definition` FOREIGN KEY (`definition_id`)
    REFERENCES `ai_workflow_definition` (`id`),
  CONSTRAINT `chk_wf_version_status` CHECK (`status` IN ('PUBLISHED','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流不可变发布版本';

-- ----------------------------
-- 3. Table structure for ai_workflow_resource_binding (工作流资源绑定)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_resource_binding` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '资源绑定主键',
  `owner_type` varchar(32) NOT NULL COMMENT '归属类型：SYSTEM系统、TENANT租户',
  `owner_id` bigint(20) NOT NULL COMMENT '归属标识，系统为0，租户为租户标识',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户标识，系统资源绑定为空',
  `scope_type` varchar(32) NOT NULL DEFAULT 'OWNER' COMMENT '绑定作用域：OWNER所有者共享、WORKFLOW工作流独享',
  `scope_id` bigint(20) NOT NULL COMMENT '作用域标识：OWNER为所有者标识、WORKFLOW为工作流定义标识',
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
  UNIQUE KEY `uk_wf_binding_key` (`owner_type`, `owner_id`, `scope_type`, `scope_id`, `environment`, `resource_kind`, `resource_key`),
  KEY `idx_wf_binding_resource` (`owner_type`, `owner_id`, `scope_type`, `scope_id`, `resource_kind`, `resource_id`),
  CONSTRAINT `chk_wf_binding_owner` CHECK (
    (`owner_type` = 'SYSTEM' AND `owner_id` = 0 AND `tenant_id` IS NULL)
    OR (`owner_type` = 'TENANT' AND `owner_id` = `tenant_id` AND `tenant_id` IS NOT NULL)
  ),
  CONSTRAINT `chk_wf_binding_environment` CHECK (`environment` IN ('DEV','TEST','PROD')),
  CONSTRAINT `chk_wf_binding_scope` CHECK (`scope_type` IN ('OWNER','WORKFLOW')),
  CONSTRAINT `chk_wf_binding_status` CHECK (`status` IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流资源绑定';

-- ----------------------------
-- 4. Table structure for ai_workflow_execution (工作流持久化执行记录)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_execution` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '执行记录主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `execution_id` varchar(64) NOT NULL COMMENT '公开执行ID',
  `parent_execution_id` varchar(64) DEFAULT NULL COMMENT '父工作流执行ID',
  `root_execution_id` varchar(64) NOT NULL COMMENT '根工作流执行ID',
  `execution_depth` int(11) NOT NULL DEFAULT '0' COMMENT '子工作流递归深度',
  `definition_id` bigint(20) NOT NULL COMMENT '工作流定义ID',
  `workflow_version_id` varchar(64) NOT NULL COMMENT '不可变公开版本ID',
  `workflow_code` varchar(64) NOT NULL COMMENT '工作流编码快照',
  `version_no` int(11) NOT NULL COMMENT '业务版本号快照',
  `plan_hash` char(64) NOT NULL COMMENT '执行计划SHA-256摘要',
  `principal_type` varchar(32) NOT NULL COMMENT '调用主体类型：ADMIN管理员、PLATFORM_USER中台用户、API_KEY接口密钥、SERVICE_ACCOUNT服务账号',
  `principal_id` varchar(128) NOT NULL COMMENT '调用主体ID',
  `idempotency_scope` varchar(192) NOT NULL COMMENT '用于请求去重的稳定租户及主体范围',
  `idempotency_key` varchar(128) DEFAULT NULL COMMENT '调用方提供的执行幂等键',
  `principal_snapshot` longtext NOT NULL COMMENT '不含密钥的授权快照JSON',
  `binding_snapshot` longtext NOT NULL COMMENT '解析后的非敏感资源绑定快照JSON',
  `environment` varchar(16) NOT NULL DEFAULT 'PROD' COMMENT '资源环境快照：DEV开发、TEST测试、PROD生产',
  `input_json` longtext NOT NULL COMMENT '执行输入JSON',
  `output_json` longtext DEFAULT NULL COMMENT '小体量最终输出JSON',
  `status` varchar(32) NOT NULL COMMENT '执行状态',
  `runner_id` varchar(64) DEFAULT NULL COMMENT '当前工作节点实例ID',
  `fencing_token` bigint(20) NOT NULL DEFAULT '0' COMMENT '工作节点单调递增隔离令牌',
  `lease_until` datetime DEFAULT NULL COMMENT '工作节点租约到期时间',
  `resume_time` datetime(3) DEFAULT NULL COMMENT '等待节点最早可恢复时间',
  `heartbeat_time` datetime DEFAULT NULL COMMENT '最近心跳时间',
  `recovery_count` int(11) NOT NULL DEFAULT '0' COMMENT '恢复尝试次数',
  `cancel_requested` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已请求持久化取消',
  `budget_json` text DEFAULT NULL COMMENT '执行预算快照JSON',
  `usage_json` text DEFAULT NULL COMMENT '已结算用量JSON',
  `quota_scopes_json` text DEFAULT NULL COMMENT '已预留并发配额范围标识JSON',
  `quota_released` tinyint(1) NOT NULL DEFAULT '0' COMMENT '并发配额是否已释放',
  `error_code` varchar(64) DEFAULT NULL COMMENT '稳定工作流错误码',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '脱敏错误信息',
  `event_sequence` bigint(20) NOT NULL DEFAULT '0' COMMENT '单调递增事件序号',
  `checkpoint_sequence` bigint(20) NOT NULL DEFAULT '0' COMMENT '单调递增检查点序号',
  `lock_version` int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `finish_time` datetime DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_execution_id` (`execution_id`),
  UNIQUE KEY `uk_wf_execution_idempotency` (`idempotency_scope`, `idempotency_key`),
  KEY `idx_wf_execution_tenant_status` (`tenant_id`, `status`, `create_time`),
  KEY `idx_wf_execution_principal` (`tenant_id`, `principal_type`, `principal_id`, `create_time`),
  KEY `idx_wf_execution_lease` (`status`, `lease_until`),
  KEY `idx_wf_execution_resume` (`status`, `resume_time`),
  KEY `idx_wf_execution_version` (`workflow_version_id`, `create_time`),
  KEY `idx_wf_execution_parent` (`parent_execution_id`, `create_time`),
  KEY `idx_wf_execution_root` (`root_execution_id`, `create_time`),
  CONSTRAINT `fk_wf_execution_definition` FOREIGN KEY (`definition_id`)
    REFERENCES `ai_workflow_definition` (`id`),
  CONSTRAINT `fk_wf_execution_version` FOREIGN KEY (`workflow_version_id`)
    REFERENCES `ai_workflow_version` (`version_id`),
  CONSTRAINT `chk_wf_execution_environment` CHECK (`environment` IN ('DEV','TEST','PROD')),
  CONSTRAINT `chk_wf_execution_status` CHECK (`status` IN (
    'QUEUED','RUNNING','WAITING_APPROVAL','WAITING_TIMER','WAITING_EVENT','RECOVERING','NEEDS_ATTENTION',
    'SUCCEEDED','FAILED','CANCELLED','REJECTED'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流持久化执行记录';

-- ----------------------------
-- 5. Table structure for ai_workflow_concurrency_quota (工作流原子并发配额)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_concurrency_quota` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '并发配额主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `scope_key` varchar(256) NOT NULL COMMENT '稳定的租户、工作流或主体范围标识',
  `scope_type` varchar(32) NOT NULL COMMENT '范围类型：TENANT租户、WORKFLOW工作流、PRINCIPAL调用主体',
  `max_active` int(11) NOT NULL COMMENT '最大活跃执行数',
  `active_count` int(11) NOT NULL DEFAULT '0' COMMENT '当前已预留执行数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_quota_scope` (`scope_key`),
  KEY `idx_wf_quota_tenant` (`tenant_id`, `scope_type`),
  CONSTRAINT `chk_wf_quota_count` CHECK (`active_count` >= 0 AND `max_active` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流原子并发配额';

-- ----------------------------
-- 6. Table structure for ai_workflow_node_run (工作流节点运行尝试记录)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_node_run` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '节点运行主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `node_run_id` varchar(64) NOT NULL COMMENT '稳定逻辑节点运行ID',
  `execution_id` varchar(64) NOT NULL COMMENT '执行ID',
  `node_id` varchar(128) NOT NULL COMMENT '定义中的节点ID',
  `branch_path` varchar(1000) NOT NULL DEFAULT '' COMMENT '稳定分支实例路径',
  `attempt_no` int(11) NOT NULL DEFAULT '1' COMMENT '尝试次数',
  `handler_version` varchar(32) NOT NULL COMMENT '锁定的处理器版本',
  `status` varchar(32) NOT NULL COMMENT '节点运行状态',
  `side_effect` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '副作用类型：NONE无、READ读、WRITE写',
  `side_effect_status` varchar(32) DEFAULT NULL COMMENT '副作用状态：NONE无、PENDING待处理、COMMITTED已提交、UNCONFIRMED未确认、COMPENSATED已补偿',
  `idempotency_key` varchar(256) DEFAULT NULL COMMENT '外部操作幂等键',
  `input_json` longtext DEFAULT NULL COMMENT '脱敏后的小体量输入JSON',
  `output_json` longtext DEFAULT NULL COMMENT '脱敏后的小体量输出JSON',
  `error_code` varchar(64) DEFAULT NULL COMMENT '稳定错误码',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '脱敏错误信息',
  `fencing_token` bigint(20) NOT NULL COMMENT '本次尝试使用的执行隔离令牌',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `finish_time` datetime DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_node_attempt` (`execution_id`, `node_run_id`, `attempt_no`),
  KEY `idx_wf_node_execution_status` (`tenant_id`, `execution_id`, `status`),
  KEY `idx_wf_node_id` (`tenant_id`, `node_id`, `create_time`),
  CONSTRAINT `fk_wf_node_execution` FOREIGN KEY (`execution_id`)
    REFERENCES `ai_workflow_execution` (`execution_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_wf_node_status` CHECK (`status` IN (
    'PENDING','READY','RUNNING','WAITING','RETRY_WAIT','SUCCEEDED','FAILED','SKIPPED','CANCELLED','NEEDS_ATTENTION'
  )),
  CONSTRAINT `chk_wf_node_side_effect` CHECK (`side_effect` IN ('NONE','READ','WRITE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点运行尝试记录';

-- ----------------------------
-- 6.1 Table structure for ai_workflow_node_test_run (单节点隔离试运行记录)
-- ----------------------------
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

-- ----------------------------
-- 6.2 Table structure for ai_workflow_node_test_audit (单节点试运行审计与费用摘要)
-- ----------------------------
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

-- ----------------------------
-- 7. Table structure for ai_workflow_checkpoint (工作流恢复检查点)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_checkpoint` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '检查点主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `execution_id` varchar(64) NOT NULL COMMENT '执行ID',
  `sequence_no` bigint(20) NOT NULL COMMENT '单调递增检查点序号',
  `plan_hash` char(64) NOT NULL COMMENT '执行计划摘要',
  `node_run_id` varchar(64) DEFAULT NULL COMMENT '最近一个安全逻辑节点运行ID',
  `state_json` longtext NOT NULL COMMENT '可序列化工作流状态JSON',
  `status` varchar(32) NOT NULL DEFAULT 'SAFE' COMMENT '状态：SAFE安全、INVALID无效、CONSUMED已使用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_checkpoint_sequence` (`execution_id`, `sequence_no`),
  KEY `idx_wf_checkpoint_tenant` (`tenant_id`, `execution_id`, `status`),
  CONSTRAINT `fk_wf_checkpoint_execution` FOREIGN KEY (`execution_id`)
    REFERENCES `ai_workflow_execution` (`execution_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_wf_checkpoint_status` CHECK (`status` IN ('SAFE','INVALID','CONSUMED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流恢复检查点';

-- ----------------------------
-- 8. Table structure for ai_workflow_event (工作流持久化事件)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_event` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '事件主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `execution_id` varchar(64) NOT NULL COMMENT '执行ID',
  `sequence_no` bigint(20) NOT NULL COMMENT '单调递增事件序号',
  `event_type` varchar(64) NOT NULL COMMENT '稳定工作流事件类型',
  `node_run_id` varchar(64) DEFAULT NULL COMMENT '关联节点运行ID',
  `node_id` varchar(128) DEFAULT NULL COMMENT '关联定义节点ID',
  `payload_json` longtext NOT NULL COMMENT '脱敏事件载荷JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_event_sequence` (`execution_id`, `sequence_no`),
  KEY `idx_wf_event_tenant` (`tenant_id`, `execution_id`, `sequence_no`),
  CONSTRAINT `fk_wf_event_execution` FOREIGN KEY (`execution_id`)
    REFERENCES `ai_workflow_execution` (`execution_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流持久化事件';

-- ----------------------------
-- 9. Table structure for ai_workflow_approval_instance (工作流审批实例)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_approval_instance` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审批实例主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `approval_instance_id` varchar(64) NOT NULL COMMENT '公开审批实例ID',
  `execution_id` varchar(64) NOT NULL COMMENT '执行ID',
  `node_run_id` varchar(64) NOT NULL COMMENT '审批节点运行ID',
  `config_version` varchar(16) NOT NULL DEFAULT '2.0' COMMENT '审批配置版本',
  `config_snapshot` longtext NOT NULL COMMENT '不可变审批配置快照JSON',
  `content_snapshot` longtext DEFAULT NULL COMMENT '提交审批的脱敏内容快照JSON',
  `result_mode` varchar(16) NOT NULL DEFAULT 'SIMPLE' COMMENT '结果模式：SIMPLE直接终止、BRANCH按结果分支',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '审批实例状态',
  `current_stage_id` varchar(64) DEFAULT NULL COMMENT '当前审批级别实例ID',
  `current_stage_sequence` int(11) DEFAULT NULL COMMENT '当前审批级别序号',
  `deadline` datetime DEFAULT NULL COMMENT '整个审批实例截止时间',
  `reminder_time` datetime DEFAULT NULL COMMENT '下一次自动提醒时间',
  `reminder_sent_time` datetime DEFAULT NULL COMMENT '最近自动提醒发送时间',
  `escalation_count` int(11) NOT NULL DEFAULT '0' COMMENT '超时升级次数',
  `lock_version` int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `finish_time` datetime DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_approval_instance_id` (`approval_instance_id`),
  UNIQUE KEY `uk_wf_approval_instance_run` (`execution_id`, `node_run_id`),
  KEY `idx_wf_approval_instance_pending` (`tenant_id`, `status`, `deadline`),
  KEY `idx_wf_approval_instance_reminder` (`tenant_id`, `status`, `reminder_time`),
  CONSTRAINT `fk_wf_approval_instance_execution` FOREIGN KEY (`execution_id`)
    REFERENCES `ai_workflow_execution` (`execution_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_wf_approval_instance_result` CHECK (`result_mode` IN ('SIMPLE','BRANCH')),
  CONSTRAINT `chk_wf_approval_instance_status` CHECK (`status` IN ('CREATED','PENDING','APPROVED','REJECTED','EXPIRED','CONFIG_ERROR','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流审批实例';

-- ----------------------------
-- 9.1 Table structure for ai_workflow_approval_stage (工作流审批级别)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_approval_stage` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审批级别主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `stage_instance_id` varchar(64) NOT NULL COMMENT '公开审批级别实例ID',
  `approval_instance_id` varchar(64) NOT NULL COMMENT '审批实例ID',
  `stage_key` varchar(64) NOT NULL COMMENT '发布配置中的稳定级别标识',
  `sequence_no` int(11) NOT NULL COMMENT '级别执行序号，从1开始',
  `stage_name` varchar(128) NOT NULL COMMENT '审批级别名称快照',
  `policy_snapshot` longtext NOT NULL COMMENT '多人决策策略快照JSON',
  `status` varchar(32) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '审批级别状态',
  `required_approvals` int(11) NOT NULL DEFAULT '1' COMMENT '本级所需通过数',
  `approved_count` int(11) NOT NULL DEFAULT '0' COMMENT '已通过人数',
  `rejected_count` int(11) NOT NULL DEFAULT '0' COMMENT '已拒绝人数',
  `pending_count` int(11) NOT NULL DEFAULT '0' COMMENT '待决定人数',
  `deadline` datetime DEFAULT NULL COMMENT '本级截止时间',
  `lock_version` int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `finish_time` datetime DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_approval_stage_id` (`stage_instance_id`),
  UNIQUE KEY `uk_wf_approval_stage_key` (`approval_instance_id`, `stage_key`),
  KEY `idx_wf_approval_stage_sequence` (`approval_instance_id`, `sequence_no`),
  KEY `idx_wf_approval_stage_pending` (`tenant_id`, `status`, `deadline`),
  CONSTRAINT `fk_wf_approval_stage_instance` FOREIGN KEY (`approval_instance_id`)
    REFERENCES `ai_workflow_approval_instance` (`approval_instance_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_wf_approval_stage_status` CHECK (`status` IN ('NOT_STARTED','ACTIVE','APPROVED','REJECTED','EXPIRED','CONFIG_ERROR','CANCELLED','SUPERSEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流审批级别';

-- ----------------------------
-- 9.2 Table structure for ai_workflow_approval_assignment (审批资格快照)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_approval_assignment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审批资格主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `assignment_id` varchar(64) NOT NULL COMMENT '公开审批资格ID',
  `approval_instance_id` varchar(64) NOT NULL COMMENT '审批实例ID',
  `stage_instance_id` varchar(64) NOT NULL COMMENT '审批级别实例ID',
  `user_id` varchar(64) NOT NULL COMMENT '审批用户ID快照',
  `username` varchar(128) DEFAULT NULL COMMENT '审批用户名快照',
  `display_name` varchar(128) DEFAULT NULL COMMENT '审批人显示名快照',
  `department_id` varchar(64) DEFAULT NULL COMMENT '部门ID快照',
  `department_name` varchar(128) DEFAULT NULL COMMENT '部门名称快照',
  `source_snapshot` longtext NOT NULL COMMENT '命中的用户/角色/部门来源快照JSON',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '审批资格状态',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_approval_assignment_id` (`assignment_id`),
  UNIQUE KEY `uk_wf_approval_assignment_user` (`stage_instance_id`, `user_id`),
  KEY `idx_wf_approval_assignment_user` (`tenant_id`, `user_id`, `status`),
  CONSTRAINT `fk_wf_approval_assignment_instance` FOREIGN KEY (`approval_instance_id`)
    REFERENCES `ai_workflow_approval_instance` (`approval_instance_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_wf_approval_assignment_stage` FOREIGN KEY (`stage_instance_id`)
    REFERENCES `ai_workflow_approval_stage` (`stage_instance_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_wf_approval_assignment_status` CHECK (`status` IN ('PENDING','APPROVED','REJECTED','REASSIGNED','REVOKED','UNAVAILABLE','EXPIRED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流审批资格快照';

-- ----------------------------
-- 9.3 Table structure for ai_workflow_approval_decision (审批决定)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_approval_decision` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审批决定主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `decision_id` varchar(64) NOT NULL COMMENT '公开审批决定ID',
  `request_id` varchar(64) NOT NULL COMMENT '客户端幂等请求ID',
  `approval_instance_id` varchar(64) NOT NULL COMMENT '审批实例ID',
  `stage_instance_id` varchar(64) NOT NULL COMMENT '审批级别实例ID',
  `assignment_id` varchar(64) NOT NULL COMMENT '审批资格ID',
  `actor_id` varchar(64) NOT NULL COMMENT '实际审批用户ID',
  `actor_name` varchar(128) DEFAULT NULL COMMENT '实际审批人显示名快照',
  `decision` varchar(16) NOT NULL COMMENT '决定：APPROVE通过、REJECT拒绝',
  `comment` varchar(2000) DEFAULT NULL COMMENT '审批意见',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '决定时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_approval_decision_id` (`decision_id`),
  UNIQUE KEY `uk_wf_approval_decision_request` (`request_id`),
  UNIQUE KEY `uk_wf_approval_decision_actor` (`stage_instance_id`, `actor_id`),
  KEY `idx_wf_approval_decision_instance` (`approval_instance_id`, `stage_instance_id`),
  CONSTRAINT `fk_wf_approval_decision_instance` FOREIGN KEY (`approval_instance_id`)
    REFERENCES `ai_workflow_approval_instance` (`approval_instance_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_wf_approval_decision_stage` FOREIGN KEY (`stage_instance_id`)
    REFERENCES `ai_workflow_approval_stage` (`stage_instance_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_wf_approval_decision_assignment` FOREIGN KEY (`assignment_id`)
    REFERENCES `ai_workflow_approval_assignment` (`assignment_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_wf_approval_decision_value` CHECK (`decision` IN ('APPROVE','REJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流审批决定';

-- ----------------------------
-- 10. Table structure for ai_workflow_artifact (工作流大体量输出产物)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_artifact` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '产物主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `artifact_id` varchar(64) NOT NULL COMMENT '公开产物ID',
  `execution_id` varchar(64) NOT NULL COMMENT '执行ID',
  `node_run_id` varchar(64) DEFAULT NULL COMMENT '产生产物的节点运行ID',
  `slot_key` varchar(64) NOT NULL DEFAULT 'result' COMMENT '同一逻辑运行内的产物槽位',
  `source_type` varchar(32) NOT NULL DEFAULT 'WORKFLOW_NODE' COMMENT '产物来源类型',
  `file_name` varchar(255) NOT NULL COMMENT '下载文件名',
  `storage_ref` varchar(2000) NOT NULL COMMENT '私有存储引用',
  `mime_type` varchar(128) DEFAULT NULL COMMENT 'MIME媒体类型',
  `size_bytes` bigint(20) NOT NULL DEFAULT '0' COMMENT '产物字节数',
  `content_hash` char(64) DEFAULT NULL COMMENT '产物SHA-256摘要',
  `status` varchar(32) NOT NULL DEFAULT 'PREPARING' COMMENT 'PREPARING/AVAILABLE/DELETING/DELETED',
  `retention_days` int(11) NOT NULL DEFAULT '30' COMMENT '执行结束后的保留天数',
  `expires_time` datetime DEFAULT NULL COMMENT '保留到期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_artifact_id` (`artifact_id`),
  UNIQUE KEY `uk_wf_artifact_logical` (`execution_id`, `node_run_id`, `slot_key`),
  KEY `idx_wf_artifact_tenant` (`tenant_id`, `execution_id`, `create_time`),
  KEY `idx_wf_artifact_expire` (`expires_time`),
  CONSTRAINT `fk_wf_artifact_execution` FOREIGN KEY (`execution_id`)
    REFERENCES `ai_workflow_execution` (`execution_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流大体量输出产物';

-- ----------------------------
-- 11. Table structure for ai_workflow_trigger (工作流触发器)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_trigger` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '触发器主键',
  `tenant_id` bigint(20) NOT NULL COMMENT '租户ID',
  `trigger_id` varchar(64) NOT NULL COMMENT '公开触发器ID',
  `definition_id` bigint(20) NOT NULL COMMENT '工作流定义ID',
  `workflow_version_id` varchar(64) NOT NULL COMMENT '锁定的已发布版本ID',
  `trigger_type` varchar(32) NOT NULL COMMENT '触发类型：SCHEDULE定时、WEBHOOK回调、EVENT事件',
  `config_json` longtext NOT NULL COMMENT '不含密钥的触发配置JSON',
  `dedup_policy_json` text DEFAULT NULL COMMENT '去重策略JSON',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE启用、DISABLED停用',
  `next_fire_time` datetime DEFAULT NULL COMMENT '下次计划触发时间，仅定时触发器使用',
  `last_fire_time` datetime DEFAULT NULL COMMENT '最近一次触发时间',
  `last_execution_id` varchar(64) DEFAULT NULL COMMENT '最近一次创建的执行ID',
  `last_trigger_status` varchar(32) DEFAULT NULL COMMENT '最近触发结果：DISPATCHED已创建执行、FAILED失败',
  `last_error_message` varchar(500) DEFAULT NULL COMMENT '最近触发失败的安全错误摘要',
  `lock_version` int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_trigger_id` (`trigger_id`),
  KEY `idx_wf_trigger_tenant` (`tenant_id`, `status`, `trigger_type`),
  KEY `idx_wf_trigger_schedule` (`status`, `trigger_type`, `next_fire_time`),
  CONSTRAINT `fk_wf_trigger_definition` FOREIGN KEY (`definition_id`)
    REFERENCES `ai_workflow_definition` (`id`),
  CONSTRAINT `fk_wf_trigger_version` FOREIGN KEY (`workflow_version_id`)
    REFERENCES `ai_workflow_version` (`version_id`),
  CONSTRAINT `chk_wf_trigger_type` CHECK (`trigger_type` IN ('SCHEDULE','WEBHOOK','EVENT')),
  CONSTRAINT `chk_wf_trigger_status` CHECK (`status` IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流触发器';

-- ----------------------------
-- 12. Table structure for ai_workflow_outbox (工作流事务消息发件箱)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_workflow_outbox` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '事务消息主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `event_id` varchar(64) NOT NULL COMMENT '稳定事务消息事件ID',
  `aggregate_type` varchar(64) NOT NULL COMMENT '聚合类型',
  `aggregate_id` varchar(64) NOT NULL COMMENT '聚合ID',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型',
  `payload_json` longtext NOT NULL COMMENT '事件载荷JSON',
  `publish_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '发布状态：PENDING待发布、PUBLISHING发布中、PUBLISHED已发布、FAILED失败',
  `attempt_count` int(11) NOT NULL DEFAULT '0' COMMENT '发布尝试次数',
  `claimed_by` varchar(96) DEFAULT NULL COMMENT '当前发布任务领取者',
  `claim_until` datetime DEFAULT NULL COMMENT '发布任务领取到期时间',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `published_time` datetime DEFAULT NULL COMMENT '发布时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_outbox_event` (`event_id`),
  KEY `idx_wf_outbox_pending` (`publish_status`, `next_retry_time`, `id`),
  KEY `idx_wf_outbox_tenant` (`tenant_id`, `aggregate_type`, `aggregate_id`),
  CONSTRAINT `chk_wf_outbox_status` CHECK (`publish_status` IN ('PENDING','PUBLISHING','PUBLISHED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流事务消息发件箱';


-- ============================================================================
-- 二、工作流菜单与权限数据初始化
-- ============================================================================

START TRANSACTION;

-- 工作流主菜单
INSERT INTO `sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
   `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES
  (9, '工作流编排', 1061, 0, 'workflow', 'ai/workflow/index', NULL, 'AiWorkflow',
   1, 0, 'C', '0', '0', 'workflow:read', 'tree-table',
   'admin', NOW(), 'admin', NOW(), '工作流编排与执行监控')
ON DUPLICATE KEY UPDATE
  `menu_name` = VALUES(`menu_name`),
  `parent_id` = VALUES(`parent_id`),
  `order_num` = VALUES(`order_num`),
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `query` = VALUES(`query`),
  `route_name` = VALUES(`route_name`),
  `is_frame` = VALUES(`is_frame`),
  `is_cache` = VALUES(`is_cache`),
  `menu_type` = VALUES(`menu_type`),
  `visible` = VALUES(`visible`),
  `status` = VALUES(`status`),
  `perms` = VALUES(`perms`),
  `icon` = VALUES(`icon`),
  `update_by` = VALUES(`update_by`),
  `update_time` = VALUES(`update_time`),
  `remark` = VALUES(`remark`);

-- 工作流按钮权限
INSERT INTO `sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
   `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES
  (2060, '工作流查询', 9, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'workflow:read', '#', 'admin', NOW(), 'admin', NOW(), ''),
  (2061, '工作流详情', 9, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'workflow:read', '#', 'admin', NOW(), 'admin', NOW(), ''),
  (2062, '工作流新增', 9, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'workflow:edit', '#', 'admin', NOW(), 'admin', NOW(), ''),
  (2063, '工作流修改', 9, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'workflow:edit', '#', 'admin', NOW(), 'admin', NOW(), ''),
  (2064, '工作流发布', 9, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'workflow:publish', '#', 'admin', NOW(), 'admin', NOW(), ''),
  (2065, '工作流执行', 9, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'workflow:execute', '#', 'admin', NOW(), 'admin', NOW(), ''),
  (2066, '工作流调试', 9, 7, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'workflow:debug', '#', 'admin', NOW(), 'admin', NOW(), ''),
  (2067, '工作流审批', 9, 8, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'workflow:approve', '#', 'admin', NOW(), 'admin', NOW(), ''),
  (2068, '工作流取消', 9, 9, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'workflow:cancel', '#', 'admin', NOW(), 'admin', NOW(), ''),
  (2069, '工作流管理', 9, 10, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'workflow:admin', '#', 'admin', NOW(), 'admin', NOW(), '')
ON DUPLICATE KEY UPDATE
  `menu_name` = VALUES(`menu_name`),
  `parent_id` = VALUES(`parent_id`),
  `order_num` = VALUES(`order_num`),
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `query` = VALUES(`query`),
  `route_name` = VALUES(`route_name`),
  `is_frame` = VALUES(`is_frame`),
  `is_cache` = VALUES(`is_cache`),
  `menu_type` = VALUES(`menu_type`),
  `visible` = VALUES(`visible`),
  `status` = VALUES(`status`),
  `perms` = VALUES(`perms`),
  `icon` = VALUES(`icon`),
  `update_by` = VALUES(`update_by`),
  `update_time` = VALUES(`update_time`),
  `remark` = VALUES(`remark`);

COMMIT;
