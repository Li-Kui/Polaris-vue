-- Polaris AI 工作流 V2.1 增量升级脚本（MySQL 5.7+/8.0）。
-- 可从仓库 V2.1 前的 ry_ai.sql 基线升级，也可在中断或成功后重复执行。

DELIMITER $$

DROP PROCEDURE IF EXISTS `_wf_add_column`$$
CREATE PROCEDURE `_wf_add_column`(
    IN p_table varchar(64), IN p_column varchar(64), IN p_definition text)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = p_table
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column
    ) THEN
        SET @wf_sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `',
                             p_column, '` ', p_definition);
        PREPARE wf_stmt FROM @wf_sql;
        EXECUTE wf_stmt;
        DEALLOCATE PREPARE wf_stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS `_wf_modify_column`$$
CREATE PROCEDURE `_wf_modify_column`(
    IN p_table varchar(64), IN p_column varchar(64), IN p_definition text)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column
    ) THEN
        SET @wf_sql = CONCAT('ALTER TABLE `', p_table, '` MODIFY COLUMN `',
                             p_column, '` ', p_definition);
        PREPARE wf_stmt FROM @wf_sql;
        EXECUTE wf_stmt;
        DEALLOCATE PREPARE wf_stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS `_wf_add_index`$$
CREATE PROCEDURE `_wf_add_index`(
    IN p_table varchar(64), IN p_index varchar(64), IN p_definition text)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = p_table
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_index
    ) THEN
        SET @wf_sql = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_definition);
        PREPARE wf_stmt FROM @wf_sql;
        EXECUTE wf_stmt;
        DEALLOCATE PREPARE wf_stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS `_wf_add_fk`$$
CREATE PROCEDURE `_wf_add_fk`(
    IN p_table varchar(64), IN p_constraint varchar(64), IN p_definition text)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.referential_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = p_table
          AND constraint_name = p_constraint
    ) THEN
        SET @wf_sql = CONCAT('ALTER TABLE `', p_table, '` ADD CONSTRAINT `',
                             p_constraint, '` ', p_definition);
        PREPARE wf_stmt FROM @wf_sql;
        EXECUTE wf_stmt;
        DEALLOCATE PREPARE wf_stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS `_wf_rebuild_legacy_checkpoint`$$
CREATE PROCEDURE `_wf_rebuild_legacy_checkpoint`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'ai_graph_checkpoint'
          AND column_name = 'thread_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'ai_graph_checkpoint'
          AND column_name = 'execution_id'
    ) THEN
        -- 旧检查点没有执行快照和所有者边界，不能安全恢复。
        DROP TABLE `ai_graph_checkpoint`;
    END IF;
END$$

CALL `_wf_rebuild_legacy_checkpoint`()$$

-- 消息表：关联工作流执行，并允许长附件 URL。
CALL `_wf_add_column`('ai_message', 'workflow_execution_id',
    'varchar(64) DEFAULT NULL COMMENT ''关联的工作流执行ID''')$$
CALL `_wf_modify_column`('ai_message', 'file_url',
    'varchar(2000) DEFAULT NULL COMMENT ''附件文件路径/链接''')$$
CALL `_wf_add_index`('ai_message', 'idx_message_workflow_execution',
    'KEY `idx_message_workflow_execution` (`workflow_execution_id`)')$$

-- 若曾部分上线并产生重复消息，保留最早记录的绑定，其余记录解除绑定后再加唯一键。
UPDATE `ai_message` m
JOIN (
    SELECT d.workflow_execution_id, d.role, MIN(d.id) AS keep_id
    FROM `ai_message` d
    WHERE d.workflow_execution_id IS NOT NULL
    GROUP BY d.workflow_execution_id, d.role
    HAVING COUNT(*) > 1
) duplicate_message
  ON duplicate_message.workflow_execution_id = m.workflow_execution_id
 AND duplicate_message.role = m.role
 AND duplicate_message.keep_id <> m.id
SET m.workflow_execution_id = NULL$$
CALL `_wf_add_index`('ai_message', 'uk_message_workflow_role',
    'UNIQUE KEY `uk_message_workflow_role` (`workflow_execution_id`, `role`)')$$

-- 工作流定义：graph_json 成为唯一运行拓扑，旧 nodes 仅保留用于人工迁移参考。
CALL `_wf_modify_column`('ai_workflow', 'nodes',
    'text DEFAULT NULL COMMENT ''旧版流程节点编排JSON（已废弃）''')$$
CALL `_wf_add_column`('ai_workflow', 'graph_json',
    'longtext DEFAULT NULL COMMENT ''图拓扑描述JSON（统一格式）''')$$
CALL `_wf_modify_column`('ai_workflow', 'graph_json',
    'longtext DEFAULT NULL COMMENT ''图拓扑描述JSON（统一格式）''')$$
CALL `_wf_add_column`('ai_workflow', 'version',
    'int(11) NOT NULL DEFAULT 1 COMMENT ''工作流定义版本（乐观锁）''')$$
UPDATE `ai_workflow`
SET `graph_json` = '{"nodes":[{"id":"migration_placeholder","type":"java","ref":"migration_placeholder","timeoutSeconds":120,"requireApproval":false}],"edges":[{"from":"__start__","to":"migration_placeholder"},{"from":"migration_placeholder","to":"__end__"}],"maxIterations":10}',
    `status` = '0',
    `version` = GREATEST(COALESCE(`version`, 1), 1),
    `remark` = CONCAT_WS('; ', NULLIF(`remark`, ''), 'V2.1升级后需在工作流编辑器重新保存拓扑')
WHERE `graph_json` IS NULL OR TRIM(`graph_json`) = ''$$
CALL `_wf_modify_column`('ai_workflow', 'graph_json',
    'longtext NOT NULL COMMENT ''图拓扑描述JSON（统一格式）''')$$
CALL `_wf_modify_column`('ai_workflow', 'version',
    'int(11) NOT NULL DEFAULT 1 COMMENT ''工作流定义版本（乐观锁）''')$$

-- 服务端拥有的不可变执行实例。
CREATE TABLE IF NOT EXISTS `ai_workflow_execution` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `execution_id` varchar(64) NOT NULL COMMENT '服务端生成的执行ID',
  `workflow_code` varchar(50) NOT NULL COMMENT '工作流编码',
  `workflow_version` int(11) NOT NULL COMMENT '执行绑定的工作流版本',
  `workflow_snapshot` longtext NOT NULL COMMENT '不可变图定义快照',
  `user_id` bigint(20) NOT NULL COMMENT '执行发起用户ID',
  `conversation_id` bigint(20) DEFAULT NULL COMMENT '关联会话ID',
  `test_run` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否为编排编辑器试运行',
  `input_text` longtext NOT NULL COMMENT '原始用户输入',
  `file_url` varchar(2000) DEFAULT NULL COMMENT '附件引用',
  `status` varchar(32) NOT NULL COMMENT '执行状态',
  `current_node_id` varchar(128) DEFAULT NULL COMMENT '当前节点实例ID',
  `result_text` longtext DEFAULT NULL COMMENT '最终输出',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '失败原因',
  `runner_id` varchar(64) DEFAULT NULL COMMENT '当前持有租约的应用实例ID',
  `lease_until` datetime DEFAULT NULL COMMENT '运行租约到期时间',
  `heartbeat_time` datetime DEFAULT NULL COMMENT '最近一次运行心跳时间',
  `event_sequence` bigint(20) NOT NULL DEFAULT 0 COMMENT '执行级SSE单调事件序号',
  `checkpoint_sequence` bigint(20) NOT NULL DEFAULT 0 COMMENT '执行级检查点单调序号',
  `lock_version` int(11) NOT NULL DEFAULT 0 COMMENT '状态乐观锁版本',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `finish_time` datetime DEFAULT NULL COMMENT '终止时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_execution_id` (`execution_id`),
  KEY `idx_workflow_execution_user_status` (`user_id`, `status`, `create_time`),
  KEY `idx_workflow_execution_code` (`workflow_code`, `workflow_version`),
  KEY `idx_workflow_execution_conversation` (`conversation_id`),
  KEY `idx_workflow_execution_lease` (`status`, `lease_until`),
  KEY `idx_workflow_execution_finish` (`status`, `finish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI工作流执行实例'$$

-- 补齐早期 V2.1 预览版执行表可能缺少的运行时字段。
CALL `_wf_add_column`('ai_workflow_execution', 'runner_id',
    'varchar(64) DEFAULT NULL COMMENT ''当前持有租约的应用实例ID''')$$
CALL `_wf_add_column`('ai_workflow_execution', 'lease_until',
    'datetime DEFAULT NULL COMMENT ''运行租约到期时间''')$$
CALL `_wf_add_column`('ai_workflow_execution', 'heartbeat_time',
    'datetime DEFAULT NULL COMMENT ''最近一次运行心跳时间''')$$
CALL `_wf_add_column`('ai_workflow_execution', 'event_sequence',
    'bigint(20) NOT NULL DEFAULT 0 COMMENT ''执行级SSE单调事件序号''')$$
CALL `_wf_add_column`('ai_workflow_execution', 'checkpoint_sequence',
    'bigint(20) NOT NULL DEFAULT 0 COMMENT ''执行级检查点单调序号''')$$
CALL `_wf_add_column`('ai_workflow_execution', 'finish_time',
    'datetime DEFAULT NULL COMMENT ''终止时间''')$$
CALL `_wf_add_index`('ai_workflow_execution', 'idx_workflow_execution_lease',
    'KEY `idx_workflow_execution_lease` (`status`, `lease_until`)')$$
CALL `_wf_add_index`('ai_workflow_execution', 'idx_workflow_execution_finish',
    'KEY `idx_workflow_execution_finish` (`status`, `finish_time`)')$$

-- 升级前的运行态没有可靠租约，统一终止，避免升级后重复执行副作用。
UPDATE `ai_workflow_execution`
SET `status` = 'FAILED',
    `error_message` = '系统升级后旧运行任务已终止，请重新执行',
    `runner_id` = NULL,
    `lease_until` = NULL,
    `heartbeat_time` = NULL,
    `finish_time` = COALESCE(`finish_time`, NOW()),
    `update_time` = NOW()
WHERE `status` IN ('QUEUED', 'RUNNING')
  AND (`lease_until` IS NULL OR `runner_id` IS NULL)$$

CREATE TABLE IF NOT EXISTS `ai_workflow_approval` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `approval_id` varchar(64) NOT NULL COMMENT '一次性审批ID',
  `execution_id` varchar(64) NOT NULL COMMENT '工作流执行ID',
  `node_instance_id` varchar(128) NOT NULL COMMENT '受保护节点实例ID',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '审批状态',
  `decision` varchar(32) DEFAULT NULL COMMENT '审批结果',
  `feedback` varchar(2000) DEFAULT NULL COMMENT '审批意见',
  `reviewer_user_id` bigint(20) DEFAULT NULL COMMENT '审批用户ID',
  `decision_time` datetime DEFAULT NULL COMMENT '决策时间',
  `lock_version` int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_approval_id` (`approval_id`),
  KEY `idx_workflow_approval_execution` (`execution_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI工作流人工审批'$$

CREATE TABLE IF NOT EXISTS `ai_graph_checkpoint` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `execution_id` varchar(64) NOT NULL COMMENT '工作流执行ID',
  `sequence_no` bigint(20) NOT NULL COMMENT '执行内单调检查点序号',
  `workflow_code` varchar(50) NOT NULL COMMENT '工作流编码',
  `user_id` bigint(20) NOT NULL COMMENT '执行用户ID',
  `conversation_id` bigint(20) DEFAULT NULL COMMENT '关联会话ID',
  `checkpoint_id` varchar(128) NOT NULL COMMENT 'checkpoint唯一ID',
  `parent_checkpoint_id` varchar(128) DEFAULT NULL COMMENT '父checkpoint ID',
  `state_json` longtext NOT NULL COMMENT '序列化状态JSON',
  `metadata_json` text DEFAULT NULL COMMENT 'checkpoint元数据JSON',
  `writes_json` text DEFAULT NULL COMMENT '中间写入操作JSON',
  `status` varchar(20) DEFAULT 'running' COMMENT '检查点状态',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_checkpoint` (`execution_id`, `checkpoint_id`),
  UNIQUE KEY `uk_execution_sequence` (`execution_id`, `sequence_no`),
  KEY `idx_execution_status` (`execution_id`, `status`),
  KEY `idx_checkpoint_user` (`user_id`, `create_time`),
  KEY `idx_checkpoint_conversation` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图工作流执行检查点'$$

DELETE a FROM `ai_workflow_approval` a
LEFT JOIN `ai_workflow_execution` e ON e.execution_id = a.execution_id
WHERE e.execution_id IS NULL$$
DELETE c FROM `ai_graph_checkpoint` c
LEFT JOIN `ai_workflow_execution` e ON e.execution_id = c.execution_id
WHERE e.execution_id IS NULL$$

CALL `_wf_add_fk`('ai_workflow_approval', 'fk_workflow_approval_execution',
    'FOREIGN KEY (`execution_id`) REFERENCES `ai_workflow_execution` (`execution_id`) ON DELETE CASCADE')$$
CALL `_wf_add_fk`('ai_graph_checkpoint', 'fk_graph_checkpoint_execution',
    'FOREIGN KEY (`execution_id`) REFERENCES `ai_workflow_execution` (`execution_id`) ON DELETE CASCADE')$$

-- 工作流按钮权限；主键已存在时保持现有记录不变。
INSERT IGNORE INTO `sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
  (2060, '工作流查询', 9, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:workflow:list', '#', 'admin', NOW(), ''),
  (2061, '工作流详情', 9, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:workflow:query', '#', 'admin', NOW(), ''),
  (2062, '工作流新增', 9, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:workflow:add', '#', 'admin', NOW(), ''),
  (2063, '工作流修改', 9, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:workflow:edit', '#', 'admin', NOW(), ''),
  (2064, '工作流删除', 9, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:workflow:remove', '#', 'admin', NOW(), ''),
  (2065, '工作流执行', 9, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:workflow:execute', '#', 'admin', NOW(), ''),
  (2066, '工作流试运行', 9, 7, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:workflow:test', '#', 'admin', NOW(), ''),
  (2067, '工作流审批', 9, 8, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:workflow:approve', '#', 'admin', NOW(), '')$$

DROP PROCEDURE IF EXISTS `_wf_rebuild_legacy_checkpoint`$$
DROP PROCEDURE IF EXISTS `_wf_add_fk`$$
DROP PROCEDURE IF EXISTS `_wf_add_index`$$
DROP PROCEDURE IF EXISTS `_wf_modify_column`$$
DROP PROCEDURE IF EXISTS `_wf_add_column`$$

DELIMITER ;
