-- MCP Client 第一阶段 A/B：租户连接管理、测试与发现预览。
-- 新项目增量 DDL，不迁移历史数据。工具审批/调用审计在后续追加 SQL 文件。
-- 请在应用启动前手动执行本文件；程序不会自动执行 DDL。
CREATE TABLE `platform_mcp_connection` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '连接ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID，不支持全局共享',
    `connection_name` varchar(100) NOT NULL COMMENT '连接名称',
    `endpoint_url` varchar(2048) NOT NULL COMMENT '完整Streamable HTTP端点',
    `auth_type` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/API_KEY/BEARER',
    `credential_header_name` varchar(100) DEFAULT NULL COMMENT 'API_KEY请求头名',
    `auth_config` json DEFAULT NULL COMMENT 'AES-GCM凭证信封，不存明文',
    `timeout_ms` int NOT NULL DEFAULT 15000 COMMENT '单次测试/发现总时限，毫秒',
    `status` char(1) NOT NULL DEFAULT '0' COMMENT '0启用1停用',
    `revision` bigint NOT NULL DEFAULT 1 COMMENT '配置修订号，乐观并发控制',
    `remark` varchar(500) DEFAULT NULL,
    `create_by` varchar(64) DEFAULT '',
    `create_time` datetime DEFAULT NULL,
    `update_by` varchar(64) DEFAULT '',
    `update_time` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_mcp_connection_tenant` (`tenant_id`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户MCP连接';


ALTER TABLE `platform_mcp_connection`
    ADD COLUMN `catalog_epoch` bigint NOT NULL DEFAULT 1 COMMENT '端点/认证上下文代次',
    ADD COLUMN `catalog_revision` bigint NOT NULL DEFAULT 0 COMMENT '完整目录刷新修订',
    ADD COLUMN `discovery_status` varchar(16) NOT NULL DEFAULT 'NEVER' COMMENT 'NEVER/SUCCESS/FAILED/STALE',
    ADD COLUMN `last_discovery_time` datetime DEFAULT NULL COMMENT '最近发现尝试时间',
    ADD COLUMN `last_discovery_success_time` datetime DEFAULT NULL COMMENT '最近完整发现成功时间',
    ADD COLUMN `last_discovery_error` varchar(64) DEFAULT NULL COMMENT '安全错误码，不保存远端原文';

CREATE TABLE `platform_mcp_tool` (
                                     `id` bigint NOT NULL AUTO_INCREMENT,
                                     `tenant_id` bigint NOT NULL,
                                     `connection_id` bigint NOT NULL,
                                     `tool_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '大小写敏感的工具名称',
                                     `tool_title` longtext DEFAULT NULL,
                                     `description` longtext DEFAULT NULL,
                                     `input_schema` json NOT NULL,
                                     `output_schema` json DEFAULT NULL,
                                     `definition_json` json NOT NULL COMMENT 'SDK解析后的完整工具定义及注解，不含连接凭证',
                                     `structure_hash` char(64) NOT NULL COMMENT '规范化定义的SHA-256',
                                     `revision` bigint NOT NULL DEFAULT 1 COMMENT '结构/目录状态/审核修改的乐观修订',
                                     `connection_epoch` bigint NOT NULL COMMENT '发现时的连接认证上下文代次',
                                     `catalog_status` varchar(16) NOT NULL DEFAULT 'PRESENT' COMMENT 'PRESENT/MISSING/STALE',
                                     `review_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REVOKED',
                                     `enabled` char(1) NOT NULL DEFAULT '1' COMMENT '0启用1禁用，新工具默认禁用',
                                     `classification` varchar(16) DEFAULT NULL COMMENT '管理员明确分类，目前只支持READ_ONLY',
                                     `schema_supported` char(1) NOT NULL DEFAULT '1' COMMENT '0支持1不支持',
                                     `schema_error` varchar(128) DEFAULT NULL COMMENT '安全Schema诊断，不存远端异常原文',
                                     `reviewed_revision` bigint DEFAULT NULL,
                                     `reviewed_by` varchar(64) DEFAULT NULL,
                                     `reviewed_time` datetime DEFAULT NULL,
                                     `review_note` varchar(500) DEFAULT NULL,
                                     `last_seen_time` datetime DEFAULT NULL,
                                     `create_by` varchar(64) DEFAULT '',
                                     `create_time` datetime DEFAULT NULL,
                                     `update_by` varchar(64) DEFAULT '',
                                     `update_time` datetime DEFAULT NULL,
                                     PRIMARY KEY (`id`),
                                     UNIQUE KEY `uk_mcp_tool_name` (`tenant_id`, `connection_id`, `tool_name`),
                                     KEY `idx_mcp_tool_review` (`tenant_id`, `connection_id`, `catalog_status`, `review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户MCP工具目录';

CREATE TABLE `platform_mcp_invocation` (
                                           `invocation_id` char(36) NOT NULL COMMENT '服务端生成的调用关联ID，不作为访问授权',
                                           `tenant_id` bigint NOT NULL,
                                           `actor_user_id` bigint DEFAULT NULL,
                                           `actor_name` varchar(64) NOT NULL,
                                           `source` varchar(16) NOT NULL COMMENT '当前仅MANUAL，工作流接入另行追加',
                                           `connection_id` bigint NOT NULL,
                                           `tool_id` bigint NOT NULL,
                                           `connection_revision` bigint NOT NULL,
                                           `catalog_revision` bigint NOT NULL,
                                           `connection_epoch` bigint NOT NULL,
                                           `tool_revision` bigint NOT NULL,
                                           `structure_hash` char(64) NOT NULL,
                                           `status` varchar(16) NOT NULL COMMENT 'STARTED/SUCCESS/FAILED/REJECTED/TOOL_ERROR/TIMEOUT/CANCELLED',
                                           `error_code` varchar(32) DEFAULT NULL COMMENT '固定安全错误码，不存远端异常原文',
                                           `call_dispatched` char(1) NOT NULL DEFAULT '0' COMMENT '1已准备派发，非远端已完成的证明',
                                           `started_time` datetime NOT NULL,
                                           `finished_time` datetime DEFAULT NULL,
                                           `duration_ms` bigint DEFAULT NULL,
                                           PRIMARY KEY (`invocation_id`),
                                           KEY `idx_mcp_invocation_tenant_time` (`tenant_id`, `started_time`, `invocation_id`),
                                           KEY `idx_mcp_invocation_tool_time` (`tenant_id`, `tool_id`, `started_time`),
                                           KEY `idx_mcp_invocation_connection_time` (`tenant_id`, `connection_id`, `started_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户MCP调用元数据审计（不保存载荷）';


ALTER TABLE `platform_mcp_invocation`
    MODIFY COLUMN `source` varchar(16) NOT NULL COMMENT 'MANUAL或WORKFLOW，由服务端设置',
    ADD COLUMN `execution_id` varchar(64) DEFAULT NULL COMMENT '工作流执行ID' AFTER `source`,
    ADD COLUMN `node_run_id` varchar(64) DEFAULT NULL COMMENT '工作流节点运行ID' AFTER `execution_id`,
    ADD COLUMN `attempt_no` int DEFAULT NULL COMMENT '节点尝试号，首期仅一次' AFTER `node_run_id`,
    ADD UNIQUE KEY `uk_mcp_workflow_attempt` (`tenant_id`, `execution_id`, `node_run_id`, `attempt_no`),
    ADD KEY `idx_mcp_invocation_execution` (`tenant_id`, `execution_id`, `started_time`);

