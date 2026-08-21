-- ----------------------------
-- 北辰中台模块建表与初始化脚本
-- ----------------------------

-- 1. 租户表
CREATE TABLE IF NOT EXISTS `platform_tenant` (
    `tenant_id`      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '租户ID',
    `tenant_name`    VARCHAR(128) NOT NULL COMMENT '租户名称',
    `tenant_code`    VARCHAR(64)  NOT NULL COMMENT '租户编码',
    `contact_name`   VARCHAR(64)  DEFAULT NULL COMMENT '联系人',
    `contact_phone`  VARCHAR(32)  DEFAULT NULL COMMENT '联系电话',
    `status`         CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
    `quota_tokens`   BIGINT       DEFAULT -1 COMMENT 'Token配额（-1无限）',
    `used_tokens`    BIGINT       DEFAULT 0 COMMENT '已用Token',
    `remark`         VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_by`      VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`tenant_id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_code`)
) ENGINE=InnoDB COMMENT='中台租户表';

-- 2. 租户用户表
CREATE TABLE IF NOT EXISTS `platform_tenant_user` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `tenant_id`       BIGINT       NOT NULL COMMENT '租户ID',
    `username`        VARCHAR(64)  NOT NULL COMMENT '用户名',
    `password`        VARCHAR(256) NOT NULL COMMENT '密码',
    `nickname`        VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `email`           VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `phone`           VARCHAR(32)  DEFAULT NULL COMMENT '手机号',
    `avatar`          VARCHAR(256) DEFAULT NULL COMMENT '头像',
    `role`            VARCHAR(32)  DEFAULT 'member' COMMENT '角色（admin/member）',
    `status`          CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
    `last_login_time` DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    `create_by`       VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_user` (`tenant_id`, `username`)
) ENGINE=InnoDB COMMENT='中台租户用户表';

-- 3. API Key 表
CREATE TABLE IF NOT EXISTS `platform_api_key` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `tenant_id`      BIGINT       NOT NULL COMMENT '租户ID',
    `api_key`        VARCHAR(128) NOT NULL COMMENT 'API Key',
    `key_name`       VARCHAR(128) NOT NULL COMMENT '密钥名称',
    `permissions`    JSON         DEFAULT NULL COMMENT '权限列表',
    `rate_limit`     INT          DEFAULT 60 COMMENT '每分钟限流',
    `status`         CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
    `expire_time`    DATETIME     DEFAULT NULL COMMENT '过期时间',
    `last_used_time` DATETIME     DEFAULT NULL COMMENT '最后使用时间',
    `create_by`      VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_api_key` (`api_key`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB COMMENT='中台API Key表';

-- 4. 外部数据源表
CREATE TABLE IF NOT EXISTS `platform_datasource` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `tenant_id`      BIGINT       DEFAULT NULL COMMENT '租户ID',
    `ds_name`        VARCHAR(128) NOT NULL COMMENT '数据源名称',
    `ds_type`        VARCHAR(32)  NOT NULL COMMENT '类型（MYSQL/POSTGRESQL/SQLSERVER）',
    `jdbc_url`       VARCHAR(512) NOT NULL COMMENT 'JDBC连接串',
    `username`       VARCHAR(128) DEFAULT NULL COMMENT '用户名',
    `password`       VARCHAR(512) DEFAULT NULL COMMENT '密码（AES加密）',
    `query_timeout`  INT          DEFAULT 30 COMMENT '查询超时(秒)',
    `read_only`      TINYINT(1)   DEFAULT 1 COMMENT '是否只读',
    `extra_config`   JSON         DEFAULT NULL COMMENT '扩展配置',
    `status`         CHAR(1)      DEFAULT '0' COMMENT '状态',
    `create_by`      VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB COMMENT='中台外部数据源表';

-- 5. 第三方 API 连接器表
CREATE TABLE IF NOT EXISTS `platform_api_connector` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `tenant_id`       BIGINT       DEFAULT NULL COMMENT '租户ID',
    `connector_name`  VARCHAR(128) NOT NULL COMMENT '连接器名称',
    `base_url`        VARCHAR(512) NOT NULL COMMENT '基础URL',
    `auth_type`       VARCHAR(32)  DEFAULT 'NONE' COMMENT '认证类型',
    `auth_config`     JSON         DEFAULT NULL COMMENT '认证配置',
    `default_headers` JSON         DEFAULT NULL COMMENT '默认请求头',
    `timeout_ms`      INT          DEFAULT 30000 COMMENT '超时(毫秒)',
    `status`          CHAR(1)      DEFAULT '0' COMMENT '状态',
    `create_by`       VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB COMMENT='中台第三方API连接器表';

-- 6. 现有 AI 表追加 tenant_id
ALTER TABLE `ai_model_config`   ADD COLUMN IF NOT EXISTS `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID' AFTER `id`;
ALTER TABLE `ai_knowledge_base` ADD COLUMN IF NOT EXISTS `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID' AFTER `id`;
ALTER TABLE `ai_conversation`   ADD COLUMN IF NOT EXISTS `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID' AFTER `id`;
ALTER TABLE `ai_agent`          ADD COLUMN IF NOT EXISTS `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID' AFTER `id`;

-- 7. 初始化管理后台「中台管理」菜单及按钮数据 (sys_menu)
-- 一级目录：中台管理
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2000, '中台管理', 0, 4, 'platformManage', NULL, '', '', 1, 0, 'M', '0', '0', '', 'component', 'admin', NOW(), '', NULL, 'AI中台管理目录');

-- 二级菜单：租户管理
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2001, '租户管理', 2000, 1, 'tenant', 'system/platform/tenant/index', '', 'PlatformTenant', 1, 0, 'C', '0', '0', 'system:tenant:list', 'peoples', 'admin', NOW(), '', NULL, '中台租户管理菜单');

-- 三级按钮权限
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2002, '租户查询', 2001, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:tenant:query', '#', 'admin', NOW(), '', NULL, '');

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2003, '租户新增', 2001, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:tenant:add', '#', 'admin', NOW(), '', NULL, '');

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2004, '租户修改', 2001, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:tenant:edit', '#', 'admin', NOW(), '', NULL, '');

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2005, '租户删除', 2001, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:tenant:remove', '#', 'admin', NOW(), '', NULL, '');
