-- 中台接口连接器增量升级脚本（2026-08-25）。
-- 适用于已经创建中台接口连接器表，但缺少认证配置、默认请求头或超时字段的数据库。
-- 本脚本只增加缺失字段，不删除表、不修改现有数据，可重复执行。

SET @platform_connector_schema_name = DATABASE();

-- 增加认证配置字段。
SET @platform_connector_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `platform_api_connector` ADD COLUMN `auth_config` json DEFAULT NULL COMMENT ''认证配置'' AFTER `auth_type`',
    'SELECT ''platform_api_connector.auth_config已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @platform_connector_schema_name
    AND TABLE_NAME = 'platform_api_connector'
    AND COLUMN_NAME = 'auth_config'
);
PREPARE platform_connector_upgrade_stmt FROM @platform_connector_upgrade_sql;
EXECUTE platform_connector_upgrade_stmt;
DEALLOCATE PREPARE platform_connector_upgrade_stmt;

-- 增加默认请求头字段。
SET @platform_connector_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `platform_api_connector` ADD COLUMN `default_headers` json DEFAULT NULL COMMENT ''默认请求头'' AFTER `auth_config`',
    'SELECT ''platform_api_connector.default_headers已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @platform_connector_schema_name
    AND TABLE_NAME = 'platform_api_connector'
    AND COLUMN_NAME = 'default_headers'
);
PREPARE platform_connector_upgrade_stmt FROM @platform_connector_upgrade_sql;
EXECUTE platform_connector_upgrade_stmt;
DEALLOCATE PREPARE platform_connector_upgrade_stmt;

-- 增加请求超时时间字段。
SET @platform_connector_upgrade_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `platform_api_connector` ADD COLUMN `timeout_ms` int DEFAULT 30000 COMMENT ''请求超时时间（毫秒）'' AFTER `default_headers`',
    'SELECT ''platform_api_connector.timeout_ms已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @platform_connector_schema_name
    AND TABLE_NAME = 'platform_api_connector'
    AND COLUMN_NAME = 'timeout_ms'
);
PREPARE platform_connector_upgrade_stmt FROM @platform_connector_upgrade_sql;
EXECUTE platform_connector_upgrade_stmt;
DEALLOCATE PREPARE platform_connector_upgrade_stmt;

SET @platform_connector_upgrade_sql = NULL;
SET @platform_connector_schema_name = NULL;
