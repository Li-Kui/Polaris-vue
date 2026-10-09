-- 1. 新增分享表（唯一新表）
CREATE TABLE IF NOT EXISTS `platform_workflow_share` (
  `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`              BIGINT       NOT NULL COMMENT '租户 ID',
  `share_code`             VARCHAR(16)  NOT NULL COMMENT '分享短码',
  `share_name`             VARCHAR(128) NOT NULL COMMENT '分享名称',
  `workflow_definition_id` BIGINT       NOT NULL COMMENT '工作流定义 ID',
  `page_type`              VARCHAR(16)  DEFAULT NULL COMMENT '页面类型覆盖，NULL 继承工作流默认',
  `page_config_json`       JSON         DEFAULT NULL COMMENT '页面配置覆盖，NULL 继承工作流默认',
  `allowed_origins`        JSON         DEFAULT NULL COMMENT 'iframe 允许嵌入的 Origin 列表',
  `rate_limit`             INT          DEFAULT 60 COMMENT '每分钟请求次数限制',
  `status`                 CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `expire_time`            DATETIME     DEFAULT NULL COMMENT '过期时间，NULL 永不过期',
  `create_by`              VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  `create_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  `update_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_share_code` (`share_code`),
  KEY `idx_share_tenant` (`tenant_id`, `status`),
  KEY `idx_share_workflow` (`tenant_id`, `workflow_definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流分享';

-- 2. 工作流定义增加默认页面配置
ALTER TABLE `ai_workflow_definition`
ADD COLUMN `default_page_type` VARCHAR(16) DEFAULT 'form'
    COMMENT '默认分享页面类型: form/chat/report/task/query/image/compare/gallery',
ADD COLUMN `share_page_config_json` JSON DEFAULT NULL
    COMMENT '默认分享页面配置 JSON';

-- 3. API Key 增加工作流级权限
ALTER TABLE `platform_api_key`
ADD COLUMN `allowed_workflows` JSON DEFAULT NULL
    COMMENT '允许调用的工作流编码列表 JSON，NULL 表示不限';
