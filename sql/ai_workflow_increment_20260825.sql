-- 工作流增量升级脚本（2026-08-25）。
-- 适用于已经创建 ai_workflow_trigger 表、但尚未补齐持久化调度字段的数据库。
-- 本脚本只增加缺失的字段和索引，不删除表、不清理数据，可重复执行。

SET @workflow_schema_name = DATABASE();

-- 增加下一次计划触发时间。
SET @workflow_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_workflow_trigger` ADD COLUMN `next_fire_time` datetime DEFAULT NULL COMMENT ''下次计划触发时间，仅定时触发器使用'' AFTER `status`',
    'SELECT ''ai_workflow_trigger.next_fire_time 已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @workflow_schema_name
    AND TABLE_NAME = 'ai_workflow_trigger'
    AND COLUMN_NAME = 'next_fire_time'
);
PREPARE workflow_upgrade_stmt FROM @workflow_upgrade_sql;
EXECUTE workflow_upgrade_stmt;
DEALLOCATE PREPARE workflow_upgrade_stmt;

-- 增加最近一次触发时间。
SET @workflow_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_workflow_trigger` ADD COLUMN `last_fire_time` datetime DEFAULT NULL COMMENT ''最近一次触发时间'' AFTER `next_fire_time`',
    'SELECT ''ai_workflow_trigger.last_fire_time 已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @workflow_schema_name
    AND TABLE_NAME = 'ai_workflow_trigger'
    AND COLUMN_NAME = 'last_fire_time'
);
PREPARE workflow_upgrade_stmt FROM @workflow_upgrade_sql;
EXECUTE workflow_upgrade_stmt;
DEALLOCATE PREPARE workflow_upgrade_stmt;

-- 增加最近一次创建的执行ID。
SET @workflow_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_workflow_trigger` ADD COLUMN `last_execution_id` varchar(64) DEFAULT NULL COMMENT ''最近一次创建的执行ID'' AFTER `last_fire_time`',
    'SELECT ''ai_workflow_trigger.last_execution_id 已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @workflow_schema_name
    AND TABLE_NAME = 'ai_workflow_trigger'
    AND COLUMN_NAME = 'last_execution_id'
);
PREPARE workflow_upgrade_stmt FROM @workflow_upgrade_sql;
EXECUTE workflow_upgrade_stmt;
DEALLOCATE PREPARE workflow_upgrade_stmt;

-- 增加最近一次触发结果。
SET @workflow_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_workflow_trigger` ADD COLUMN `last_trigger_status` varchar(32) DEFAULT NULL COMMENT ''最近触发结果：DISPATCHED已创建执行、FAILED失败'' AFTER `last_execution_id`',
    'SELECT ''ai_workflow_trigger.last_trigger_status 已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @workflow_schema_name
    AND TABLE_NAME = 'ai_workflow_trigger'
    AND COLUMN_NAME = 'last_trigger_status'
);
PREPARE workflow_upgrade_stmt FROM @workflow_upgrade_sql;
EXECUTE workflow_upgrade_stmt;
DEALLOCATE PREPARE workflow_upgrade_stmt;

-- 增加最近一次触发失败的安全错误摘要。
SET @workflow_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_workflow_trigger` ADD COLUMN `last_error_message` varchar(500) DEFAULT NULL COMMENT ''最近触发失败的安全错误摘要'' AFTER `last_trigger_status`',
    'SELECT ''ai_workflow_trigger.last_error_message 已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @workflow_schema_name
    AND TABLE_NAME = 'ai_workflow_trigger'
    AND COLUMN_NAME = 'last_error_message'
);
PREPARE workflow_upgrade_stmt FROM @workflow_upgrade_sql;
EXECUTE workflow_upgrade_stmt;
DEALLOCATE PREPARE workflow_upgrade_stmt;

-- 增加定时触发器扫描索引。
SET @workflow_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_workflow_trigger` ADD INDEX `idx_wf_trigger_schedule` (`status`, `trigger_type`, `next_fire_time`)',
    'SELECT ''ai_workflow_trigger.idx_wf_trigger_schedule 已存在，跳过''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @workflow_schema_name
    AND TABLE_NAME = 'ai_workflow_trigger'
    AND INDEX_NAME = 'idx_wf_trigger_schedule'
);
PREPARE workflow_upgrade_stmt FROM @workflow_upgrade_sql;
EXECUTE workflow_upgrade_stmt;
DEALLOCATE PREPARE workflow_upgrade_stmt;

SET @workflow_upgrade_sql = NULL;
SET @workflow_schema_name = NULL;
