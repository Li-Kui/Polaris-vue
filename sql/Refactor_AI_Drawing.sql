-- ----------------------------
-- Table structure for ai_image_task
-- ----------------------------
DROP TABLE IF EXISTS `ai_image_task`;
CREATE TABLE `ai_image_task` (
  `task_id` varchar(64) NOT NULL COMMENT '任务ID (UUID)',
  `prompt` varchar(1000) NOT NULL COMMENT '生成提示词',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '任务状态（0生成中 1生成成功 2生成失败）',
  `image_url` text DEFAULT NULL COMMENT '生成的图片OSS/本地路径',
  `error_msg` text DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI图像生成任务表';

-- ----------------------------
-- Alter table ai_model_config for Polymorphic Use Cases
-- ----------------------------
-- 1. 新增模型大分类类型字段 (CHAT / EMBEDDING / IMAGE)
ALTER TABLE `ai_model_config` ADD COLUMN `model_type` varchar(32) NOT NULL DEFAULT 'CHAT' COMMENT '模型用途类型';

-- 2. 新增当前模型启用的工具箱白名单列表字段 (例如存储 "web_search,image_generate")
ALTER TABLE `ai_model_config` ADD COLUMN `enabled_tools` varchar(500) DEFAULT NULL COMMENT '当前模型启用的工具集白名单';

-- 3. 平滑数据迁移：将旧的 is_default_embedding 迁移至统一的 model_type 机制
UPDATE `ai_model_config` SET `model_type` = 'CHAT' WHERE `is_default` = '1' OR `is_default` = '0';
UPDATE `ai_model_config` SET `model_type` = 'EMBEDDING', `is_default` = '1' WHERE `is_default_embedding` = '1';

-- 4. 删除旧的向量默认列
ALTER TABLE `ai_model_config` DROP COLUMN `is_default_embedding`;

-- ----------------------------
-- Insert Ruoyi Dict data
-- ----------------------------
-- AI模型用途大分类字典
INSERT INTO `sys_dict_type` (dict_name, dict_type, status, create_by, create_time) 
VALUES ('AI模型分类', 'sys_ai_model_type', '0', 'admin', NOW());

INSERT INTO `sys_dict_data` (dict_sort, dict_label, dict_value, dict_type, status, create_by, create_time) VALUES 
(1, '文本对话模型', 'CHAT', 'sys_ai_model_type', '0', 'admin', NOW()),
(2, '向量提取模型', 'EMBEDDING', 'sys_ai_model_type', '0', 'admin', NOW()),
(3, '图像生成模型', 'IMAGE', 'sys_ai_model_type', '0', 'admin', NOW());

-- AI工具箱列表字典
INSERT INTO `sys_dict_type` (dict_name, dict_type, status, create_by, create_time) 
VALUES ('AI工具列表', 'sys_ai_tools', '0', 'admin', NOW());

INSERT INTO `sys_dict_data` (dict_sort, dict_label, dict_value, dict_type, status, create_by, create_time) VALUES 
(1, '网络实时搜索', 'web_search', 'sys_ai_tools', '0', 'admin', NOW()),
(2, 'AI图像生成', 'image_generate', 'sys_ai_tools', '0', 'admin', NOW());

-- ============ ai_model_config 扩展：图像模型能力配置 ============

ALTER TABLE ai_model_config
  ADD COLUMN image_capabilities VARCHAR(500) DEFAULT NULL COMMENT '图像能力JSON数组' AFTER search_key,
  ADD COLUMN model_features     VARCHAR(500) DEFAULT NULL COMMENT '模型质量标签JSON数组' AFTER image_capabilities,
  ADD COLUMN default_image_size VARCHAR(20)  DEFAULT '1024x1024' COMMENT '默认出图尺寸' AFTER model_features,
  ADD COLUMN model_description  VARCHAR(255) DEFAULT NULL COMMENT '模型备注' AFTER default_image_size;

-- ============ ai_image_task 扩展：支持10种能力+数据权限+图片转存 ============

ALTER TABLE ai_image_task
  ADD COLUMN generation_mode VARCHAR(50)  DEFAULT 'text_to_image' COMMENT '生成能力(10种之一)' AFTER prompt,
  ADD COLUMN model_config_id BIGINT       DEFAULT NULL COMMENT '模型配置ID' AFTER generation_mode,
  ADD COLUMN conversation_id BIGINT       DEFAULT NULL COMMENT '会话ID' AFTER model_config_id,
  ADD COLUMN provider        VARCHAR(50)  DEFAULT NULL COMMENT '厂商' AFTER conversation_id,
  ADD COLUMN source_images   TEXT         DEFAULT NULL COMMENT '输入图URL列表JSON' AFTER provider,
  ADD COLUMN mask_image      VARCHAR(500) DEFAULT NULL COMMENT '遮罩图URL' AFTER source_images,
  ADD COLUMN image_params    TEXT         DEFAULT NULL COMMENT '参数JSON' AFTER mask_image,
  ADD COLUMN cost_time       BIGINT       DEFAULT NULL COMMENT '耗时(ms)' AFTER image_url,
  ADD COLUMN create_by       VARCHAR(64)  DEFAULT NULL COMMENT '创建者' AFTER cost_time,
  ADD COLUMN dept_id         BIGINT       DEFAULT NULL COMMENT '部门ID' AFTER create_by,
  ADD COLUMN update_time     DATETIME     DEFAULT NULL COMMENT '更新时间' AFTER create_time;



-- ============ 绘图工坊菜单（显式绘图面板） ============
INSERT INTO `sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
  (10, '绘图工坊', 1061, 0, 'aiDraw', 'ai/draw', '', '', 1, 0, 'C', '0', '0', '', 'picture', 'admin', NOW(), 'AI绘图工坊');
