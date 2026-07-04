-- ----------------------------
-- Table structure for ai_knowledge_base
-- ----------------------------
DROP TABLE IF EXISTS `ai_knowledge_base`;
CREATE TABLE `ai_knowledge_base` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '知识库名称',
  `description` varchar(500) DEFAULT NULL COMMENT '知识库描述',
  `dept_id` bigint(20) DEFAULT NULL COMMENT '部门ID',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识库表';

-- ----------------------------
-- Table structure for ai_document
-- ----------------------------
DROP TABLE IF EXISTS `ai_document`;
CREATE TABLE `ai_document` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `knowledge_base_id` bigint(20) NOT NULL COMMENT '知识库ID',
  `name` varchar(255) NOT NULL COMMENT '文档名称',
  `file_url` varchar(500) NOT NULL COMMENT '文件OSS/本地路径',
  `status` char(1) DEFAULT '0' COMMENT '状态（0待解析 1解析中 2已解析 3失败）',
  `word_count` int(11) DEFAULT '0' COMMENT '总字数',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_kb_id` (`knowledge_base_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- ----------------------------
-- Table structure for ai_conversation
-- ----------------------------
DROP TABLE IF EXISTS `ai_conversation`;
CREATE TABLE `ai_conversation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '会话id',
  `title` varchar(100) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  `user_id` bigint(20) NOT NULL COMMENT '用户id',
  `model` varchar(50) NOT NULL DEFAULT 'qwen-plus' COMMENT '使用模型',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `knowledge_base_id` bigint(20) DEFAULT NULL COMMENT '关联的知识库id',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ai对话会话表';

-- ----------------------------
-- Table structure for ai_message
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ai对话消息表';

-- ----------------------------
-- Records of sys_menu (AI对话菜单)
-- ----------------------------
-- 插入前尝试清理，防止主键冲突
DELETE FROM `sys_menu` WHERE `menu_id` = 5;
INSERT INTO `sys_menu` VALUES ('5', 'AI对话', '0', '0', 'aiChat', 'ai/chat', '', '', '1', '0', 'C', '0', '0', '', 'user', 'admin', SYSDATE(), '', NULL, 'AI对话地址');

DELETE FROM `sys_menu` WHERE `menu_id` = 6;
INSERT INTO `sys_menu` VALUES ('6', '知识库管理', '0', '0', 'aiKnowledge', 'ai/knowledge', '', '', '1', '0', 'C', '0', '0', '', 'folder', 'admin', SYSDATE(), '', NULL, 'AI知识库管理');

DELETE FROM `sys_menu` WHERE `menu_id` = 7;
INSERT INTO `sys_menu` VALUES ('7', '模型管理', '0', '0', 'aiModel', 'ai/model', '', '', '1', '0', 'C', '0', '0', '', 'cpu', 'admin', SYSDATE(), '', NULL, 'AI模型管理');

-- ----------------------------
-- Table structure for ai_model_config
-- ----------------------------
DROP TABLE IF EXISTS `ai_model_config`;
CREATE TABLE `ai_model_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '配置名称',
  `provider` varchar(50) NOT NULL COMMENT '提供商(dashscope/openai/deepseek/ollama)',
  `model_name` varchar(100) NOT NULL COMMENT '模型名称',
  `api_key` varchar(255) DEFAULT NULL COMMENT 'API Key',
  `base_url` varchar(255) DEFAULT NULL COMMENT 'API Base URL',
  `max_tokens` int(11) DEFAULT '2048' COMMENT '最大Token数',
  `temperature` double DEFAULT '0.7' COMMENT '随机温度',
  `max_history_messages` int(11) DEFAULT '20' COMMENT '最大历史消息数',
  `system_prompt` varchar(1000) DEFAULT NULL COMMENT '模型专属系统提示词',
  `is_default` char(1) DEFAULT '0' COMMENT '是否默认聊天模型(1是 0否)',
  `is_default_embedding` char(1) DEFAULT '0' COMMENT '是否默认向量模型(1是 0否)',
  `enable_thinking` char(1) DEFAULT '0' COMMENT '是否开启思考模式(1是 0否)',
  `reasoning_effort` varchar(20) DEFAULT NULL COMMENT '思考强度(low/medium/high/max)',
  `dept_id` bigint(20) DEFAULT NULL COMMENT '部门ID',
  `enable_search` char(1) DEFAULT '0' COMMENT '是否启用联网搜索(1是 0否)',
  `search_key` varchar(255) DEFAULT NULL COMMENT '联网搜索 API Key',
  `status` char(1) DEFAULT '1' COMMENT '状态(1正常 0禁用)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型配置表';

-- ----------------------------
-- Records of ai_model_config
-- ----------------------------
INSERT INTO `ai_model_config` (name, provider, model_name, api_key, max_tokens, temperature, max_history_messages, system_prompt, is_default, is_default_embedding, status, create_by, create_time)
VALUES ('DeepSeek官方聊天', 'deepseek', 'deepseek-chat', 'sk-您的Key', 4096, 0.7, 20, '你是一个由 DeepSeek 提供技术支持的 AI 助手。', '1', '0', '1', 'admin', NOW());

INSERT INTO `ai_model_config` (name, provider, model_name, api_key, base_url, is_default, is_default_embedding, status, create_by, create_time)
VALUES ('阿里通义向量', 'dashscope', 'text-embedding-v3', 'sk-您的Key', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '0', '1', '1', 'admin', NOW());

-- ----------------------------
-- 升级脚本 (已建表旧环境请在数据库中单独执行以下语句进行升级)
-- ----------------------------
-- ALTER TABLE `ai_model_config` ADD COLUMN `enable_search` CHAR(1) DEFAULT '0' COMMENT '是否启用联网搜索 (1是 0否)';
-- ALTER TABLE `ai_model_config` ADD COLUMN `search_key` VARCHAR(255) DEFAULT NULL COMMENT '联网搜索 API Key';
