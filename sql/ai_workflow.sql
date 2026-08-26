-- 工作流正式表结构。历史工作流数据将直接清理，不做兼容迁移。
SET FOREIGN_KEY_CHECKS = 0;
-- 部分 SQL 客户端会逐条执行语句且不保留会话设置，因此历史子表必须先于父表删除。
DROP TABLE IF EXISTS `ai_graph_checkpoint`;
DROP TABLE IF EXISTS `ai_workflow_approval`;
DROP TABLE IF EXISTS `ai_workflow_outbox`;
DROP TABLE IF EXISTS `ai_workflow_trigger`;
DROP TABLE IF EXISTS `ai_workflow_artifact`;
DROP TABLE IF EXISTS `ai_workflow_approval_task`;
DROP TABLE IF EXISTS `ai_workflow_event`;
DROP TABLE IF EXISTS `ai_workflow_checkpoint`;
DROP TABLE IF EXISTS `ai_workflow_node_run`;
DROP TABLE IF EXISTS `ai_workflow_concurrency_quota`;
DROP TABLE IF EXISTS `ai_workflow_resource_binding`;
DROP TABLE IF EXISTS `ai_workflow_execution`;
DROP TABLE IF EXISTS `ai_workflow_version`;
DROP TABLE IF EXISTS `ai_workflow_definition`;
DROP TABLE IF EXISTS `ai_workflow`;
SET FOREIGN_KEY_CHECKS = 1;

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

CREATE TABLE IF NOT EXISTS `ai_workflow_resource_binding` (
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
  `resume_time` datetime DEFAULT NULL COMMENT '等待节点最早可恢复时间',
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
    'QUEUED','RUNNING','WAITING_APPROVAL','WAITING_EVENT','RECOVERING','NEEDS_ATTENTION',
    'SUCCEEDED','FAILED','CANCELLED','REJECTED'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流持久化执行记录';

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

CREATE TABLE IF NOT EXISTS `ai_workflow_approval_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审批任务主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `approval_task_id` varchar(64) NOT NULL COMMENT '公开的一次性审批任务ID',
  `execution_id` varchar(64) NOT NULL COMMENT '执行ID',
  `node_run_id` varchar(64) NOT NULL COMMENT '受保护的逻辑节点运行ID',
  `assignee_type` varchar(32) NOT NULL COMMENT '审批人类型：USER用户、ROLE角色、DEPARTMENT部门、EXPRESSION表达式',
  `assignee_snapshot` longtext NOT NULL COMMENT '不可变审批人策略快照JSON',
  `approval_mode` varchar(32) NOT NULL COMMENT '审批模式：ANY任一、ALL全部、SEQUENTIAL依次、N_OF_M多人中指定数量',
  `required_approvals` int(11) NOT NULL DEFAULT '1' COMMENT '多人中指定数量模式所需通过数',
  `allow_self_approval` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否允许发起人自行审批',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待审批、APPROVED已通过、REJECTED已拒绝、EXPIRED已过期、CANCELLED已取消',
  `decision_summary` text DEFAULT NULL COMMENT '脱敏审批决定摘要JSON',
  `deadline` datetime DEFAULT NULL COMMENT '审批截止时间',
  `lock_version` int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `finish_time` datetime DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_approval_task_id` (`approval_task_id`),
  KEY `idx_wf_approval_pending` (`tenant_id`, `status`, `deadline`),
  KEY `idx_wf_approval_execution` (`tenant_id`, `execution_id`, `status`),
  CONSTRAINT `fk_wf_approval_execution` FOREIGN KEY (`execution_id`)
    REFERENCES `ai_workflow_execution` (`execution_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_wf_approval_status` CHECK (`status` IN ('PENDING','APPROVED','REJECTED','EXPIRED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流审批任务';

CREATE TABLE IF NOT EXISTS `ai_workflow_artifact` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '产物主键',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `artifact_id` varchar(64) NOT NULL COMMENT '公开产物ID',
  `execution_id` varchar(64) NOT NULL COMMENT '执行ID',
  `node_run_id` varchar(64) DEFAULT NULL COMMENT '产生产物的节点运行ID',
  `file_name` varchar(255) NOT NULL COMMENT '下载文件名',
  `storage_ref` varchar(2000) NOT NULL COMMENT '私有存储引用',
  `mime_type` varchar(128) DEFAULT NULL COMMENT 'MIME媒体类型',
  `size_bytes` bigint(20) NOT NULL DEFAULT '0' COMMENT '产物字节数',
  `content_hash` char(64) DEFAULT NULL COMMENT '产物SHA-256摘要',
  `expires_time` datetime DEFAULT NULL COMMENT '保留到期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_artifact_id` (`artifact_id`),
  KEY `idx_wf_artifact_tenant` (`tenant_id`, `execution_id`, `create_time`),
  KEY `idx_wf_artifact_expire` (`expires_time`),
  CONSTRAINT `fk_wf_artifact_execution` FOREIGN KEY (`execution_id`)
    REFERENCES `ai_workflow_execution` (`execution_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流大体量输出产物';

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

-- ----------------------------
-- 工作流菜单与按钮权限
-- ----------------------------
-- 旧按钮ID的权限语义已经变化，仅清理仍关联旧权限编码的角色授权。
-- 脚本重复执行时，新权限编码对应的角色授权不会被再次清理。
-- 不删除2090，该菜单ID属于“安全策略”，与工作流无关。
START TRANSACTION;

DELETE role_menu
FROM `sys_role_menu` role_menu
INNER JOIN `sys_menu` menu ON menu.`menu_id` = role_menu.`menu_id`
WHERE menu.`menu_id` IN (2060, 2061, 2062, 2063, 2064, 2065, 2066, 2067, 2068, 2069)
  AND menu.`perms` LIKE 'ai:workflow:%';

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
