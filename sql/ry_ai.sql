-- ----------------------------
-- Polaris AI 模块完整数据库建表与初始化脚本
-- ----------------------------

-- ----------------------------
-- 1. Table structure for ai_model_config (AI模型配置表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_model_config`;
CREATE TABLE `ai_model_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '配置名称',
  `provider` varchar(50) NOT NULL COMMENT '提供商(dashscope/openai/deepseek/ollama)',
  `model_name` varchar(100) NOT NULL COMMENT '模型名称',
  `api_key` varchar(255) DEFAULT NULL COMMENT 'API Key',
  `base_url` varchar(255) DEFAULT NULL COMMENT 'API Base URL',
  `model_type` varchar(32) NOT NULL DEFAULT 'CHAT' COMMENT '模型用途类型(CHAT/EMBEDDING/IMAGE)',
  `max_tokens` int(11) DEFAULT '2048' COMMENT '最大Token数',
  `temperature` double DEFAULT '0.7' COMMENT '随机温度',
  `max_history_messages` int(11) DEFAULT '20' COMMENT '最大历史消息数',
  `system_prompt` varchar(1000) DEFAULT NULL COMMENT '模型专属系统提示词',
  `is_default` char(1) DEFAULT '0' COMMENT '是否默认模型(1是 0否)',
  `enable_thinking` char(1) DEFAULT '0' COMMENT '是否开启思考模式(1是 0否)',
  `reasoning_effort` varchar(20) DEFAULT NULL COMMENT '思考强度(low/medium/high/max)',
  `enable_search` char(1) DEFAULT '0' COMMENT '是否启用联网搜索(1是 0否)',
  `search_key` varchar(255) DEFAULT NULL COMMENT '联网搜索 API Key',
  `image_capabilities` varchar(500) DEFAULT NULL COMMENT '图像能力JSON数组',
  `model_features` varchar(500) DEFAULT NULL COMMENT '模型质量标签JSON数组',
  `default_image_size` varchar(20) DEFAULT '1024x1024' COMMENT '默认出图尺寸',
  `model_description` varchar(255) DEFAULT NULL COMMENT '模型备注',
  `enabled_tools` varchar(500) DEFAULT NULL COMMENT '当前模型启用的工具集白名单',
  `dept_id` bigint(20) DEFAULT NULL COMMENT '部门ID',
  `status` char(1) DEFAULT '1' COMMENT '状态(1正常 0禁用)',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型配置表';

-- ----------------------------
-- Records of ai_model_config
-- ----------------------------
INSERT INTO `ai_model_config` (name, provider, model_name, api_key, base_url, model_type, max_tokens, temperature, max_history_messages, system_prompt, is_default, status, create_by, create_time)
VALUES ('DeepSeek官方聊天', 'deepseek', 'deepseek-chat', 'sk-您的Key', NULL, 'CHAT', 4096, 0.7, 20, '你是一个由 DeepSeek 提供技术支持的 AI 助手。', '1', '1', 'admin', NOW());

INSERT INTO `ai_model_config` (name, provider, model_name, api_key, base_url, model_type, is_default, status, create_by, create_time)
VALUES ('阿里通义向量', 'dashscope', 'text-embedding-v3', 'sk-您的Key', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'EMBEDDING', '1', '1', 'admin', NOW());

-- ----------------------------
-- 2. Table structure for ai_knowledge_base (AI知识库表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_knowledge_base`;
CREATE TABLE `ai_knowledge_base` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '知识库名称',
  `description` varchar(500) DEFAULT NULL COMMENT '知识库描述',
  `dept_id` bigint(20) DEFAULT NULL COMMENT '部门ID',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识库表';

-- ----------------------------
-- 3. Table structure for ai_document (知识库文档表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_document`;
CREATE TABLE `ai_document` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `knowledge_base_id` bigint(20) NOT NULL COMMENT '知识库ID',
  `name` varchar(255) NOT NULL COMMENT '文档名称',
  `file_url` varchar(500) NOT NULL COMMENT '文件OSS/本地路径',
  `status` char(1) DEFAULT '0' COMMENT '状态（0待解析 1解析中 2已解析 3失败）',
  `word_count` int(11) DEFAULT '0' COMMENT '总字数',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_kb_id` (`knowledge_base_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- ----------------------------
-- 4. Table structure for ai_conversation (AI对话会话表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_conversation`;
CREATE TABLE `ai_conversation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '会话id',
  `title` varchar(100) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  `user_id` bigint(20) NOT NULL COMMENT '用户id',
  `model` varchar(50) NOT NULL DEFAULT 'qwen-plus' COMMENT '使用模型',
  `model_config_id` bigint(20) DEFAULT NULL COMMENT '所选大模型配置ID',
  `knowledge_base_id` bigint(20) DEFAULT NULL COMMENT '关联的知识库id',
  `agent_code` varchar(64) DEFAULT NULL COMMENT '关联的智能体Code',
  `workflow_code` varchar(64) DEFAULT NULL COMMENT '关联的工作流Code',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话会话表';

-- ----------------------------
-- 5. Table structure for ai_message (AI对话消息表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_message`;
CREATE TABLE `ai_message` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '消息id',
  `conversation_id` bigint(20) NOT NULL COMMENT '会话id',
  `role` varchar(20) NOT NULL COMMENT '角色(user/assistant)',
  `content` longtext NOT NULL COMMENT '消息内容',
  `reasoning_content` longtext DEFAULT NULL COMMENT '思考过程内容',
  `tokens` int(11) DEFAULT '0' COMMENT '消耗token数',
  `file_url` varchar(500) DEFAULT NULL COMMENT '附件文件路径/链接',
  `file_name` varchar(255) DEFAULT NULL COMMENT '附件原始名称',
  `file_content` longtext DEFAULT NULL COMMENT '附件解析内容',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息表';

-- ----------------------------
-- 6. Table structure for ai_agent (AI智能体定义表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent`;
CREATE TABLE `ai_agent` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_code` varchar(50) NOT NULL COMMENT '智能体唯一编码(用于引擎检索)',
  `agent_name` varchar(100) NOT NULL COMMENT '智能体名称',
  `model_name` varchar(100) NOT NULL DEFAULT 'deepseek-chat' COMMENT '底座大模型名称',
  `model_config_id` bigint(20) DEFAULT NULL COMMENT '所选大模型配置ID',
  `system_prompt` text COMMENT '系统角色提示词(System Prompt)',
  `temperature` double DEFAULT '0.2' COMMENT '随机温度',
  `tools` varchar(255) DEFAULT NULL COMMENT '绑定工具类列表，英文逗号分隔(如: SysUserTools)',
  `status` char(1) DEFAULT '1' COMMENT '状态(1启用 0禁用)',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志(0存在 2删除)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_code` (`agent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI智能体定义表';

-- ----------------------------
-- Records of ai_agent
-- ----------------------------
INSERT INTO `ai_agent` (agent_code, agent_name, model_name, system_prompt, temperature, tools, status, create_by, create_time)
VALUES 
('intent_router', '意图分发员', 'deepseek-chat', '你是一个路由分发员。请分析用户的意图，决定是要查询系统用户、搜索全网还是普通闲聊。', 0.1, '', '1', 'admin', NOW()),
('sys_user_analyst', '系统用户审计师', 'deepseek-chat', '你是一个严谨的系统用户分析审计专家。你可以通过调用提供的系统工具 queryUserList 获取当前系统里的用户，并基于检索到的数据，写出一份系统安全评估与分析报告。', 0.2, 'SysUserTools', '1', 'admin', NOW());

-- ----------------------------
-- 7. Table structure for ai_workflow (AI智能体工作流表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_workflow`;
CREATE TABLE `ai_workflow` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `workflow_code` varchar(50) NOT NULL COMMENT '工作流唯一编码',
  `workflow_name` varchar(100) NOT NULL COMMENT '工作流名称',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `nodes` text NOT NULL COMMENT '流程节点编排JSON (如: ["intent_route", "sys_user_analyst", "reporter"])',
  `graph_json` text DEFAULT NULL COMMENT '图拓扑描述JSON（统一格式）',
  `status` char(1) DEFAULT '1' COMMENT '状态(1启用 0禁用)',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志(0存在 2删除)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_code` (`workflow_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI智能体工作流表';

-- ----------------------------
-- Records of ai_workflow
-- ----------------------------
INSERT INTO `ai_workflow` (workflow_code, workflow_name, description, nodes, status, create_by, create_time)
VALUES 
('sys_user_audit', '系统用户安全审计流', '全自动系统用户检索、数据安全分析的一键式流程', '["intent_router", "sys_user_analyst"]', '1', 'admin', NOW());

-- ----------------------------
-- 8. Table structure for ai_graph_checkpoint (图工作流执行检查点表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_graph_checkpoint`;
CREATE TABLE `ai_graph_checkpoint` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `thread_id` varchar(128) NOT NULL COMMENT '会话线程ID',
  `workflow_code` varchar(128) NOT NULL COMMENT '工作流编码',
  `checkpoint_id` varchar(128) NOT NULL COMMENT 'checkpoint唯一ID',
  `parent_checkpoint_id` varchar(128) DEFAULT NULL COMMENT '父checkpoint ID',
  `state_json` longtext NOT NULL COMMENT '序列化的AgentState纯数据JSON',
  `metadata_json` text DEFAULT NULL COMMENT 'checkpoint元数据JSON',
  `writes_json` text DEFAULT NULL COMMENT '中间写入操作JSON',
  `status` varchar(20) DEFAULT 'running' COMMENT '状态：running/paused/done/error',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_thread_checkpoint` (`thread_id`, `checkpoint_id`),
  KEY `idx_thread_status` (`thread_id`, `status`),
  KEY `idx_workflow_code` (`workflow_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图工作流执行检查点';

-- ----------------------------
-- 9. Table structure for ai_image_task (AI图像生成任务表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_image_task`;
CREATE TABLE `ai_image_task` (
  `task_id` varchar(64) NOT NULL COMMENT '任务ID (UUID)',
  `prompt` varchar(1000) NOT NULL COMMENT '生成提示词',
  `generation_mode` varchar(50) DEFAULT 'text_to_image' COMMENT '生成能力(10种之一)',
  `model_config_id` bigint(20) DEFAULT NULL COMMENT '模型配置ID',
  `conversation_id` bigint(20) DEFAULT NULL COMMENT '会话ID',
  `provider` varchar(50) DEFAULT NULL COMMENT '厂商',
  `source_images` text DEFAULT NULL COMMENT '输入图URL列表JSON',
  `mask_image` varchar(500) DEFAULT NULL COMMENT '遮罩图URL',
  `image_params` text DEFAULT NULL COMMENT '参数JSON',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '任务状态（0生成中 1生成成功 2生成失败）',
  `image_url` text DEFAULT NULL COMMENT '生成的图片OSS/本地路径',
  `error_msg` text DEFAULT NULL COMMENT '失败原因',
  `cost_time` bigint(20) DEFAULT NULL COMMENT '耗时(ms)',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `dept_id` bigint(20) DEFAULT NULL COMMENT '部门ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI图像生成任务表';

-- ----------------------------
-- 10. Ruoyi 系统字典数据 (AI相关)
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

-- ----------------------------
-- 11. Ruoyi 系统菜单数据 (AI绘图工坊菜单)
-- ----------------------------
INSERT INTO `sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
  (2050, '绘图工坊', 2000, 6, 'draw', 'ai/draw/index', NULL, 'AiDraw',
   1, 0, 'C', '0', '0', 'ai:draw:list', 'image', 'admin', NOW(), 'AI高级绘图工坊页面');

-- ----------------------------
-- 增量升级 SQL 脚本（已有数据库环境直接执行以下部分）
-- ----------------------------
-- 补齐 ai_conversation 表的智能体与工作流绑定字段
ALTER TABLE `ai_conversation` ADD COLUMN `agent_code` varchar(64) DEFAULT NULL COMMENT '关联的智能体Code';
ALTER TABLE `ai_conversation` ADD COLUMN `workflow_code` varchar(64) DEFAULT NULL COMMENT '关联的工作流Code';

-- ----------------------------
-- 12. Table structure for ai_report (AI分析报告表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_report`;
CREATE TABLE `ai_report` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '报告ID',
  `report_code` varchar(64) DEFAULT NULL COMMENT '报告编号',
  `report_title` varchar(255) NOT NULL COMMENT '报告标题',
  `conversation_id` bigint(20) DEFAULT NULL COMMENT '关联会话ID',
  `agent_code` varchar(64) DEFAULT NULL COMMENT '关联智能体编码',
  `report_content` longtext COMMENT '报告Markdown正文',
  `report_stats` text COMMENT '统计指标JSON',
  `refined_schema` longtext COMMENT '重塑美化JSON',
  `refine_status` varchar(16) DEFAULT 'NONE' COMMENT '美化处理状态',
  `refined_source_hash` varchar(64) DEFAULT NULL COMMENT '美化结果对应的原文SHA-256',
  `refine_schema_version` varchar(32) DEFAULT NULL COMMENT '美化结果Schema版本',
  `refine_prompt_version` varchar(32) DEFAULT NULL COMMENT '美化Prompt版本',
  `refine_started_at` datetime DEFAULT NULL COMMENT '美化任务开始时间',
  `refined_at` datetime DEFAULT NULL COMMENT '美化完成时间',
  `refine_error` varchar(500) DEFAULT NULL COMMENT '美化失败原因',
  `user_id` bigint(20) DEFAULT NULL COMMENT '所属用户ID',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1归档）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析报告归档表';

-- 已安装系统升级脚本：请在确认目标列不存在后执行，重复执行会因列已存在而失败。
-- ALTER TABLE `ai_report` ADD COLUMN `refined_schema` longtext COMMENT '重塑美化JSON' AFTER `report_stats`;
-- ALTER TABLE `ai_report` ADD COLUMN `refine_status` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '美化处理状态' AFTER `refined_schema`;
-- ALTER TABLE `ai_report` ADD COLUMN `refined_source_hash` varchar(64) DEFAULT NULL COMMENT '美化结果对应的原文SHA-256' AFTER `refine_status`;
-- ALTER TABLE `ai_report` ADD COLUMN `refine_schema_version` varchar(32) DEFAULT NULL COMMENT '美化结果Schema版本' AFTER `refined_source_hash`;
-- ALTER TABLE `ai_report` ADD COLUMN `refine_prompt_version` varchar(32) DEFAULT NULL COMMENT '美化Prompt版本' AFTER `refine_schema_version`;
-- ALTER TABLE `ai_report` ADD COLUMN `refined_at` datetime DEFAULT NULL COMMENT '美化完成时间' AFTER `refine_prompt_version`;
-- ALTER TABLE `ai_report` ADD COLUMN `refine_error` varchar(500) DEFAULT NULL COMMENT '美化失败原因' AFTER `refined_at`;
-- UPDATE `ai_report` SET `refine_status` = 'NONE' WHERE `refine_status` IS NULL OR `refine_status` = '';

-- ----------------------------
-- 13. Ruoyi 系统菜单数据 (AI分析报告中心菜单)
-- ----------------------------
INSERT INTO `sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
  (2051, '报告中心', 1061, 7, 'report', 'ai/report', NULL, 'AiReport',
   1, 0, 'C', '0', '0', 'ai:report:list', 'document', 'admin', NOW(), 'AI分析报告归档管理页面');
