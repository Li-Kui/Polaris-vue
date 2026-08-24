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
  `embedding_dimension` int(11) DEFAULT NULL COMMENT '向量模型输出维度',
  `embedding_dimension_mode` varchar(20) DEFAULT 'MODEL_DEFAULT' COMMENT '维度模式(MODEL_DEFAULT/REQUEST)',
  `embedding_max_input_tokens` int(11) DEFAULT NULL COMMENT '向量模型最大输入Token数',
  `embedding_batch_size` int(11) DEFAULT '16' COMMENT '向量化批量大小',
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
  `access_mode` varchar(20) DEFAULT 'direct' COMMENT '连接方式(direct直连 relay中转站)',
  `max_concurrency` int(11) DEFAULT NULL COMMENT '在途最大渲染并发数上限',
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

INSERT INTO `ai_model_config` (name, provider, model_name, api_key, base_url, model_type, embedding_dimension, embedding_dimension_mode, embedding_batch_size, is_default, status, create_by, create_time)
VALUES ('阿里通义向量', 'dashscope', 'text-embedding-v3', 'sk-您的Key', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'EMBEDDING', 1024, 'MODEL_DEFAULT', 16, '1', '1', 'admin', NOW());

-- ----------------------------
-- 2. Table structure for ai_knowledge_base (AI知识库表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_knowledge_base`;
CREATE TABLE `ai_knowledge_base` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '知识库名称',
  `description` varchar(500) DEFAULT NULL COMMENT '知识库描述',
  `embedding_model_id` bigint(20) DEFAULT NULL COMMENT '绑定的向量模型配置ID',
  `vector_collection` varchar(200) DEFAULT NULL COMMENT '当前生效的向量collection',
  `chunk_size` int(11) NOT NULL DEFAULT '300' COMMENT '切片最大字符数',
  `chunk_overlap` int(11) NOT NULL DEFAULT '30' COMMENT '切片重叠字符数',
  `splitter_type` varchar(32) NOT NULL DEFAULT 'RECURSIVE' COMMENT '切片算法',
  `retrieval_top_k` int(11) NOT NULL DEFAULT '5' COMMENT '最大召回数量',
  `retrieval_min_score` double NOT NULL DEFAULT '0.5' COMMENT '最低相似度',
  `index_version` bigint(20) NOT NULL DEFAULT '0' COMMENT '当前索引版本',
  `index_signature` varchar(64) DEFAULT NULL COMMENT '索引配置签名',
  `index_status` varchar(20) NOT NULL DEFAULT 'EMPTY' COMMENT '索引状态',
  `index_error` varchar(1000) DEFAULT NULL COMMENT '最近索引失败原因',
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
  `moderation_status` varchar(20) NOT NULL DEFAULT 'WAIT_SCAN' COMMENT 'WAIT_SCAN/SCANNING/SAFE/QUARANTINED/SCAN_FAILED/AUTO_DELETED',
  `moderation_event_id` bigint(20) DEFAULT NULL COMMENT '最近检测事件ID',
  `moderation_version` bigint(20) DEFAULT NULL COMMENT '检测使用的词库版本ID',
  `quarantine_path` varchar(1000) DEFAULT NULL COMMENT '隔离文件私有路径',
  `quarantine_expire_time` datetime DEFAULT NULL COMMENT '隔离文件到期时间',
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
  `workflow_execution_id` varchar(64) DEFAULT NULL COMMENT '关联的工作流执行ID',
  `role` varchar(20) NOT NULL COMMENT '角色(user/assistant)',
  `content` longtext NOT NULL COMMENT '消息内容',
  `reasoning_content` longtext DEFAULT NULL COMMENT '思考过程内容',
  `tokens` int(11) DEFAULT '0' COMMENT '消耗token数',
  `file_url` varchar(2000) DEFAULT NULL COMMENT '附件文件路径/链接',
  `file_name` varchar(255) DEFAULT NULL COMMENT '附件原始名称',
  `file_content` longtext DEFAULT NULL COMMENT '附件解析内容',
  `attachment_tokens` varchar(2000) DEFAULT NULL COMMENT 'AI私有附件令牌列表',
  `moderation_status` varchar(32) DEFAULT NULL COMMENT '安全检测状态：SAFE, INTERRUPTED_BLOCKED',
  `moderation_event_id` bigint(20) DEFAULT NULL COMMENT '安全检测事件ID',
  `moderation_version` bigint(20) DEFAULT NULL COMMENT '生效的词库版本号',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_message_workflow_execution` (`workflow_execution_id`),
  UNIQUE KEY `uk_message_workflow_role` (`workflow_execution_id`, `role`)
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
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `workflow_code` varchar(50) NOT NULL COMMENT '工作流唯一编码',
  `workflow_name` varchar(100) NOT NULL COMMENT '工作流名称',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `nodes` text DEFAULT NULL COMMENT '旧版流程节点编排JSON（已废弃）',
  `graph_json` longtext NOT NULL COMMENT '图拓扑描述JSON（统一格式）',
  `version` int(11) NOT NULL DEFAULT '1' COMMENT '工作流定义版本（乐观锁）',
  `status` char(1) DEFAULT '1' COMMENT '状态(1启用 0禁用)',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志(0存在 2删除)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_code` (`workflow_code`),
  KEY `idx_ai_workflow_tenant` (`tenant_id`, `status`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI智能体工作流表';

-- ----------------------------
-- Records of ai_workflow
-- ----------------------------
INSERT INTO `ai_workflow` (workflow_code, workflow_name, description, nodes, graph_json, version, status, create_by, create_time)
VALUES 
('sys_user_audit', '系统用户安全审计流', '全自动系统用户检索、数据安全分析的一键式流程',
 '["intent_router", "sys_user_analyst"]',
 '{"nodes":[{"id":"intent_router","type":"agent","ref":"intent_router","timeoutSeconds":120,"requireApproval":false},{"id":"sys_user_analyst","type":"agent","ref":"sys_user_analyst","timeoutSeconds":120,"requireApproval":false}],"edges":[{"from":"__start__","to":"intent_router"},{"from":"intent_router","to":"sys_user_analyst"},{"from":"sys_user_analyst","to":"__end__"}],"maxIterations":10}',
 1, '1', 'admin', NOW());

-- ----------------------------
-- 8. Table structure for ai_workflow_execution (工作流执行实例表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_graph_checkpoint`;
DROP TABLE IF EXISTS `ai_workflow_approval`;
DROP TABLE IF EXISTS `ai_workflow_execution`;
CREATE TABLE `ai_workflow_execution` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `execution_id` varchar(64) NOT NULL COMMENT '服务端生成的执行ID',
  `workflow_code` varchar(50) NOT NULL COMMENT '工作流编码',
  `workflow_version` int(11) NOT NULL COMMENT '执行绑定的工作流版本',
  `workflow_snapshot` longtext NOT NULL COMMENT '不可变图定义快照',
  `user_id` bigint(20) NOT NULL COMMENT '执行发起用户ID',
  `conversation_id` bigint(20) DEFAULT NULL COMMENT '关联会话ID',
  `test_run` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否为编排编辑器试运行（1是 0否）',
  `input_text` longtext NOT NULL COMMENT '原始用户输入',
  `file_url` varchar(2000) DEFAULT NULL COMMENT '附件引用',
  `attachment_tokens` varchar(2000) DEFAULT NULL COMMENT 'AI私有附件令牌列表',
  `status` varchar(32) NOT NULL COMMENT 'QUEUED/RUNNING/WAITING_APPROVAL/SUCCEEDED/FAILED/CANCELLED/REJECTED',
  `current_node_id` varchar(128) DEFAULT NULL COMMENT '当前等待或执行节点实例ID',
  `result_text` longtext DEFAULT NULL COMMENT '最终输出',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '失败原因',
  `runner_id` varchar(64) DEFAULT NULL COMMENT '当前持有租约的应用实例ID',
  `lease_until` datetime DEFAULT NULL COMMENT '运行租约到期时间',
  `heartbeat_time` datetime DEFAULT NULL COMMENT '最近一次运行心跳时间',
  `event_sequence` bigint(20) NOT NULL DEFAULT '0' COMMENT '执行级SSE单调事件序号',
  `checkpoint_sequence` bigint(20) NOT NULL DEFAULT '0' COMMENT '执行级检查点单调序号',
  `lock_version` int(11) NOT NULL DEFAULT '0' COMMENT '状态乐观锁版本',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `finish_time` datetime DEFAULT NULL COMMENT '终止时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_execution_id` (`execution_id`),
  KEY `idx_workflow_execution_user_status` (`user_id`, `status`, `create_time`),
  KEY `idx_workflow_execution_tenant_user_status` (`tenant_id`, `user_id`, `status`, `create_time`),
  KEY `idx_workflow_execution_code` (`workflow_code`, `workflow_version`),
  KEY `idx_workflow_execution_conversation` (`conversation_id`),
  KEY `idx_workflow_execution_lease` (`status`, `lease_until`),
  KEY `idx_workflow_execution_finish` (`status`, `finish_time`),
  CONSTRAINT `chk_workflow_execution_status` CHECK (`status` IN
    ('QUEUED','RUNNING','WAITING_APPROVAL','SUCCEEDED','FAILED','CANCELLED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI工作流执行实例';

-- ----------------------------
-- 9. Table structure for ai_workflow_approval (工作流人工审批表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_workflow_approval`;
CREATE TABLE `ai_workflow_approval` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `approval_id` varchar(64) NOT NULL COMMENT '一次性审批ID',
  `execution_id` varchar(64) NOT NULL COMMENT '工作流执行ID',
  `node_instance_id` varchar(128) NOT NULL COMMENT '受保护节点实例ID',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
  `decision` varchar(32) DEFAULT NULL COMMENT 'approved/rejected/cancelled',
  `feedback` varchar(2000) DEFAULT NULL COMMENT '审批意见',
  `reviewer_user_id` bigint(20) DEFAULT NULL COMMENT '审批用户ID',
  `decision_time` datetime DEFAULT NULL COMMENT '决策时间',
  `lock_version` int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_approval_id` (`approval_id`),
  KEY `idx_workflow_approval_execution` (`execution_id`, `status`),
  CONSTRAINT `chk_workflow_approval_status` CHECK (`status` IN
    ('PENDING','APPROVED','REJECTED','CANCELLED')),
  CONSTRAINT `fk_workflow_approval_execution` FOREIGN KEY (`execution_id`)
    REFERENCES `ai_workflow_execution` (`execution_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI工作流人工审批';

-- ----------------------------
-- 10. Table structure for ai_graph_checkpoint (图工作流执行检查点表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_graph_checkpoint`;
CREATE TABLE `ai_graph_checkpoint` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `execution_id` varchar(64) NOT NULL COMMENT '工作流执行ID',
  `sequence_no` bigint(20) NOT NULL COMMENT '执行内单调检查点序号',
  `workflow_code` varchar(50) NOT NULL COMMENT '工作流编码',
  `user_id` bigint(20) NOT NULL COMMENT '执行用户ID',
  `conversation_id` bigint(20) DEFAULT NULL COMMENT '关联会话ID',
  `checkpoint_id` varchar(128) NOT NULL COMMENT 'checkpoint唯一ID',
  `parent_checkpoint_id` varchar(128) DEFAULT NULL COMMENT '父checkpoint ID',
  `state_json` longtext NOT NULL COMMENT '序列化的AgentState纯数据JSON',
  `metadata_json` text DEFAULT NULL COMMENT 'checkpoint元数据JSON',
  `writes_json` text DEFAULT NULL COMMENT '中间写入操作JSON',
  `status` varchar(20) DEFAULT 'running' COMMENT '状态：running/paused/done/error',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_checkpoint` (`execution_id`, `checkpoint_id`),
  UNIQUE KEY `uk_execution_sequence` (`execution_id`, `sequence_no`),
  KEY `idx_execution_status` (`execution_id`, `status`),
  KEY `idx_checkpoint_user` (`user_id`, `create_time`),
  KEY `idx_checkpoint_conversation` (`conversation_id`),
  CONSTRAINT `chk_graph_checkpoint_status` CHECK (`status` IN
    ('running','paused','done','error','cancelled','rejected')),
  CONSTRAINT `fk_graph_checkpoint_execution` FOREIGN KEY (`execution_id`)
    REFERENCES `ai_workflow_execution` (`execution_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图工作流执行检查点';

-- ----------------------------
-- 11. Table structure for ai_image_task (AI图像生成任务表)
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

-- ----------------------------
-- 13. Table structure for ai_moderation_dictionary_version (敏感内容词库版本表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_moderation_dictionary_version`;
CREATE TABLE `ai_moderation_dictionary_version` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `version_no` varchar(100) NOT NULL COMMENT '词库版本号',
  `checksum` char(64) NOT NULL COMMENT '规则规范化SHA-256校验和',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
  `source_version` varchar(100) NOT NULL COMMENT '种子来源版本',
  `source_location` varchar(500) DEFAULT NULL COMMENT '种子资源位置',
  `published_by` varchar(100) DEFAULT NULL COMMENT '发布或回滚操作人',
  `published_time` datetime DEFAULT NULL COMMENT '发布时间',
  `published_slot` tinyint GENERATED ALWAYS AS
    (CASE WHEN `status` = 'PUBLISHED' THEN 1 ELSE NULL END) STORED COMMENT '唯一已发布槽位',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_moderation_version_no` (`version_no`),
  UNIQUE KEY `uk_moderation_version_checksum` (`checksum`),
  UNIQUE KEY `uk_moderation_one_published` (`published_slot`),
  KEY `idx_moderation_version_status` (`status`, `published_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感内容词库版本';

-- ----------------------------
-- 14. Table structure for ai_moderation_rule (敏感内容规则表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_moderation_rule`;
CREATE TABLE `ai_moderation_rule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dictionary_version_id` bigint(20) NOT NULL COMMENT '词库版本ID',
  `rule_type` varchar(32) NOT NULL COMMENT 'RISK_WORD/RISK_CONTEXT/SAFE_CONTEXT/ALLOW_TERM',
  `content` varchar(1000) NOT NULL COMMENT '规则原文',
  `normalized_content` varchar(1000) NOT NULL COMMENT '归一化规则内容',
  `normalized_hash` char(64) NOT NULL COMMENT '归一化规则内容SHA-256',
  `category` varchar(64) NOT NULL COMMENT '风险分类',
  `weight` int(11) NOT NULL DEFAULT '0' COMMENT '风险分权重',
  `source` varchar(200) DEFAULT NULL COMMENT '规则来源',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_moderation_rule_normalized`
    (`dictionary_version_id`, `rule_type`, `normalized_hash`, `category`),
  KEY `idx_moderation_rule_version_type` (`dictionary_version_id`, `rule_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感内容规则';

-- ----------------------------
-- 15. Table structure for ai_moderation_policy (敏感内容场景策略表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_moderation_policy`;
CREATE TABLE `ai_moderation_policy` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scene` varchar(32) NOT NULL COMMENT 'KNOWLEDGE/CHAT_INPUT/AI_OUTPUT/WORKFLOW_INPUT/WORKFLOW_OUTPUT',
  `preset` varchar(20) NOT NULL COMMENT 'LENIENT/BALANCED/STRICT',
  `mode` varchar(20) NOT NULL DEFAULT 'OBSERVE' COMMENT 'OBSERVE/ENFORCE',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `suspect_threshold` int(11) DEFAULT 40 COMMENT '疑似阈值',
  `block_threshold` int(11) DEFAULT 70 COMMENT '阻断阈值',
  `provider_enabled` tinyint(1) DEFAULT 0 COMMENT '是否启用第三方检测',
  `provider_timeout_ms` int(11) DEFAULT 1500 COMMENT '第三方检测超时毫秒数',
  `provider_daily_limit` int(11) DEFAULT 1000 COMMENT '第三方每日调用上限',
  `provider_monthly_budget` decimal(12,2) DEFAULT 100.00 COMMENT '第三方月度预算',
  `segment_chars` int(11) DEFAULT 200 COMMENT '分段字符数',
  `segment_overlap_chars` int(11) DEFAULT 64 COMMENT '分段重叠字符数',
  `output_buffer_chars` int(11) DEFAULT 300 COMMENT '输出检测缓冲字符数',
  `quarantine_days` int(11) DEFAULT 7 COMMENT '知识隔离天数',
  `policy_version` bigint(20) DEFAULT 1 COMMENT '策略版本',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_moderation_policy_scene` (`scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感内容场景策略';

-- ----------------------------
-- 16. Table structure for ai_moderation_event (敏感内容检测审计事件表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_moderation_event`;
CREATE TABLE `ai_moderation_event` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_id` varchar(64) NOT NULL COMMENT '随机检测请求标识',
  `scene` varchar(32) NOT NULL COMMENT '检测场景',
  `resource_type` varchar(64) DEFAULT NULL COMMENT '内部资源类型',
  `resource_id` varchar(128) DEFAULT NULL COMMENT '内部资源标识',
  `dictionary_version` bigint(20) DEFAULT NULL COMMENT '词库版本ID',
  `policy_version` bigint(20) DEFAULT NULL COMMENT '策略版本',
  `local_decision` varchar(20) NOT NULL COMMENT 'PASS/SUSPECT/HIGH_RISK',
  `final_action` varchar(20) NOT NULL COMMENT 'ALLOW/BLOCK/QUARANTINE/REPLACE',
  `risk_score` int(11) NOT NULL DEFAULT '0' COMMENT '风险分',
  `categories` varchar(1000) DEFAULT NULL COMMENT '命中分类列表',
  `matched_rule_ids` varchar(1000) DEFAULT NULL COMMENT '命中规则ID列表',
  `provider` varchar(64) DEFAULT NULL COMMENT '第三方提供商',
  `provider_request_id` varchar(128) DEFAULT NULL COMMENT '第三方请求标识',
  `provider_decision` varchar(64) DEFAULT NULL COMMENT '第三方结论',
  `provider_latency_ms` bigint(20) DEFAULT NULL COMMENT '第三方耗时毫秒数',
  `fallback_reason` varchar(64) DEFAULT NULL COMMENT '降级原因',
  `content_hash` char(64) NOT NULL COMMENT '待审内容SHA-256，不保存原文',
  `masked_excerpt` varchar(120) DEFAULT NULL COMMENT '最多120字符的脱敏摘要',
  `expire_time` datetime NOT NULL COMMENT '审计到期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_moderation_event_request` (`request_id`),
  KEY `idx_moderation_event_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感内容检测审计事件';

-- ----------------------------
-- 17. Table structure for ai_moderation_candidate (敏感内容候选规则表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_moderation_candidate`;
CREATE TABLE `ai_moderation_candidate` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `candidate_term` varchar(256) NOT NULL COMMENT '受控候选词或短表达，不得保存原始待审内容',
  `expression_hash` char(64) NOT NULL COMMENT '候选表达SHA-256',
  `masked_excerpt` varchar(120) DEFAULT NULL COMMENT '最多120字符的脱敏证据摘要',
  `category` varchar(64) NOT NULL COMMENT '建议分类',
  `source_event_id` bigint(20) DEFAULT NULL COMMENT '来源检测事件ID',
  `source_signal` varchar(64) NOT NULL COMMENT 'PROVIDER_HIGH_RISK/LOCAL_OBFUSCATION_REPEAT',
  `observation_count` int(11) NOT NULL DEFAULT 1 COMMENT '已观察次数',
  `status` varchar(20) NOT NULL DEFAULT 'OBSERVING' COMMENT 'OBSERVING/PENDING/ACCEPTED/REJECTED',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_moderation_candidate_expression` (`expression_hash`, `category`),
  KEY `idx_moderation_candidate_status` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感内容候选规则';

-- ----------------------------
-- 18. Table structure for ai_private_attachment (AI私有附件暂存表)
-- ----------------------------
DROP TABLE IF EXISTS `ai_private_attachment`;
CREATE TABLE `ai_private_attachment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `attachment_token` varchar(128) NOT NULL COMMENT '私有附件令牌',
  `user_id` bigint(20) NOT NULL COMMENT '上传用户ID',
  `original_name` varchar(500) NOT NULL COMMENT '原始文件名',
  `storage_path` varchar(1000) NOT NULL COMMENT '私有暂存路径',
  `status` varchar(20) NOT NULL DEFAULT 'WAIT_SCAN' COMMENT 'WAIT_SCAN/SCANNING/SAFE/REJECTED/EXPIRED',
  `expire_time` datetime NOT NULL COMMENT '到期清理时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_private_attachment_token` (`attachment_token`),
  KEY `idx_private_attachment_cleanup` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI私有附件暂存';

-- ----------------------------
-- 19. Ruoyi 系统字典数据 (AI相关)
-- ----------------------------
-- AI模型用途大分类字典
INSERT INTO `sys_dict_type` (dict_name, dict_type, status, create_by, create_time) 
VALUES ('AI模型分类', 'sys_ai_model_type', '0', 'admin', NOW())
ON DUPLICATE KEY UPDATE `status` = VALUES(`status`);

INSERT INTO `sys_dict_data` (dict_sort, dict_label, dict_value, dict_type, status, create_by, create_time) VALUES 
(1, '文本对话模型', 'CHAT', 'sys_ai_model_type', '0', 'admin', NOW()),
(2, '向量提取模型', 'EMBEDDING', 'sys_ai_model_type', '0', 'admin', NOW()),
(3, '图像生成模型', 'IMAGE', 'sys_ai_model_type', '0', 'admin', NOW())
ON DUPLICATE KEY UPDATE `dict_label` = VALUES(`dict_label`);

-- AI工具箱列表字典
INSERT INTO `sys_dict_type` (dict_name, dict_type, status, create_by, create_time) 
VALUES ('AI工具列表', 'sys_ai_tools', '0', 'admin', NOW())
ON DUPLICATE KEY UPDATE `status` = VALUES(`status`);

INSERT INTO `sys_dict_data` (dict_sort, dict_label, dict_value, dict_type, status, create_by, create_time) VALUES 
(1, '网络实时搜索', 'web_search', 'sys_ai_tools', '0', 'admin', NOW()),
(2, 'AI图像生成', 'image_generate', 'sys_ai_tools', '0', 'admin', NOW())
ON DUPLICATE KEY UPDATE `dict_label` = VALUES(`dict_label`);

-- ----------------------------
-- 20. Ruoyi 系统菜单与权限数据
-- ----------------------------
-- AI绘图工坊菜单
INSERT INTO `sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
  (2050, '绘图工坊', 1061, 6, 'draw', 'ai/draw/index', NULL, 'AiDraw',
   1, 0, 'C', '0', '0', 'ai:draw:list', 'image', 'admin', NOW(), 'AI高级绘图工坊页面')
ON DUPLICATE KEY UPDATE `component` = VALUES(`component`);

-- AI分析报告中心菜单
INSERT INTO `sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
  (2051, '报告中心', 1061, 7, 'report', 'ai/report', NULL, 'AiReport',
   1, 0, 'C', '0', '0', 'ai:report:list', 'document', 'admin', NOW(), 'AI分析报告归档管理页面')
ON DUPLICATE KEY UPDATE `component` = VALUES(`component`);

-- 工作流 V2.1 按钮权限
INSERT INTO `sys_menu`
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
  (2067, '工作流审批', 9, 8, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:workflow:approve', '#', 'admin', NOW(), '')
ON DUPLICATE KEY UPDATE `perms` = VALUES(`perms`);

-- 安全检测管理菜单配置 (动态挂载至AI模块下)
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT '安全策略', m.menu_id, 20, 'moderationPolicy', 'ai/moderation/policy', 1, 0, 'C', '0', '0', 'ai:moderation:policy:list', 'shield', 'admin', NOW(), 'AI安全策略管理'
FROM `sys_menu` m
WHERE m.path = 'ai' OR m.component = 'Layout' AND m.menu_name LIKE '%AI%'
LIMIT 1
ON DUPLICATE KEY UPDATE `component` = VALUES(`component`);

INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT '安全词库', m.menu_id, 21, 'moderationDictionary', 'ai/moderation/dictionary', 1, 0, 'C', '0', '0', 'ai:moderation:dictionary:list', 'dict', 'admin', NOW(), 'AI敏感词库管理'
FROM `sys_menu` m
WHERE m.path = 'ai' OR m.component = 'Layout' AND m.menu_name LIKE '%AI%'
LIMIT 1
ON DUPLICATE KEY UPDATE `component` = VALUES(`component`);

INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT '安全统计', m.menu_id, 22, 'moderationStatistics', 'ai/moderation/statistics', 1, 0, 'C', '0', '0', 'ai:moderation:statistics:list', 'chart', 'admin', NOW(), 'AI安全统计与运维'
FROM `sys_menu` m
WHERE m.path = 'ai' OR m.component = 'Layout' AND m.menu_name LIKE '%AI%'
LIMIT 1
ON DUPLICATE KEY UPDATE `component` = VALUES(`component`);

-- ----------------------------
-- 21. AI机器安全检测自动清理定时任务 (每15分钟执行一次)
-- ----------------------------
INSERT INTO `sys_job`
  (`job_name`, `job_group`, `invoke_target`, `cron_expression`,
   `misfire_policy`, `concurrent`, `status`, `create_by`, `create_time`, `remark`)
SELECT
  'AI机器安全检测自动清理', 'AI_MODERATION', 'moderationCleanupTask.run',
  '0 0/15 * * * ?', '3', '1', '0', 'admin', NOW(),
  '清理到期隔离文件、私有附件、审计事件和候选'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_job`
  WHERE `job_group` = 'AI_MODERATION'
    AND `invoke_target` = 'moderationCleanupTask.run'
);


-- ============================================================================
-- 增量升级 SQL 脚本汇总（已有老版本数据库环境按需执行以下部分）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 【增量 1】智能体与工作流字段升级
-- ----------------------------------------------------------------------------
-- ALTER TABLE `ai_conversation` ADD COLUMN `agent_code` varchar(64) DEFAULT NULL COMMENT '关联的智能体Code';
-- ALTER TABLE `ai_conversation` ADD COLUMN `workflow_code` varchar(64) DEFAULT NULL COMMENT '关联的工作流Code';

-- ----------------------------------------------------------------------------
-- 【增量 2】分析报告与美化 Schema 字段升级
-- ----------------------------------------------------------------------------
-- ALTER TABLE `ai_report` ADD COLUMN `refined_schema` longtext COMMENT '重塑美化JSON' AFTER `report_stats`;
-- ALTER TABLE `ai_report` ADD COLUMN `refine_status` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '美化处理状态' AFTER `refined_schema`;
-- ALTER TABLE `ai_report` ADD COLUMN `refined_source_hash` varchar(64) DEFAULT NULL COMMENT '美化结果对应的原文SHA-256' AFTER `refine_status`;
-- ALTER TABLE `ai_report` ADD COLUMN `refine_schema_version` varchar(32) DEFAULT NULL COMMENT '美化结果Schema版本' AFTER `refined_source_hash`;
-- ALTER TABLE `ai_report` ADD COLUMN `refine_prompt_version` varchar(32) DEFAULT NULL COMMENT '美化Prompt版本' AFTER `refine_schema_version`;
-- ALTER TABLE `ai_report` ADD COLUMN `refined_at` datetime DEFAULT NULL COMMENT '美化完成时间' AFTER `refine_prompt_version`;
-- ALTER TABLE `ai_report` ADD COLUMN `refine_error` varchar(500) DEFAULT NULL COMMENT '美化失败原因' AFTER `refined_at`;
-- UPDATE `ai_report` SET `refine_status` = 'NONE' WHERE `refine_status` IS NULL OR `refine_status` = '';

-- ----------------------------------------------------------------------------
-- 【增量 3】AI 向量模型与知识库索引参数升级
-- ----------------------------------------------------------------------------
-- ALTER TABLE `ai_model_config`
--   ADD COLUMN `embedding_dimension` int(11) DEFAULT NULL COMMENT '向量模型输出维度' AFTER `model_type`,
--   ADD COLUMN `embedding_dimension_mode` varchar(20) DEFAULT 'MODEL_DEFAULT' COMMENT '维度模式(MODEL_DEFAULT/REQUEST)' AFTER `embedding_dimension`,
--   ADD COLUMN `embedding_max_input_tokens` int(11) DEFAULT NULL COMMENT '向量模型最大输入Token数' AFTER `embedding_dimension_mode`,
--   ADD COLUMN `embedding_batch_size` int(11) DEFAULT '16' COMMENT '向量化批量大小' AFTER `embedding_max_input_tokens`;

-- UPDATE `ai_model_config`
-- SET `embedding_dimension` = 1024
-- WHERE `model_type` = 'EMBEDDING'
--   AND `embedding_dimension` IS NULL
--   AND `model_name` = 'text-embedding-v3';

-- ALTER TABLE `ai_knowledge_base`
--   ADD COLUMN `embedding_model_id` bigint(20) DEFAULT NULL COMMENT '绑定的向量模型配置ID' AFTER `description`,
--   ADD COLUMN `vector_collection` varchar(200) DEFAULT NULL COMMENT '当前生效的向量collection' AFTER `embedding_model_id`,
--   ADD COLUMN `chunk_size` int(11) NOT NULL DEFAULT '300' COMMENT '切片最大字符数' AFTER `vector_collection`,
--   ADD COLUMN `chunk_overlap` int(11) NOT NULL DEFAULT '30' COMMENT '切片重叠字符数' AFTER `chunk_size`,
--   ADD COLUMN `splitter_type` varchar(32) NOT NULL DEFAULT 'RECURSIVE' COMMENT '切片算法' AFTER `chunk_overlap`,
--   ADD COLUMN `retrieval_top_k` int(11) NOT NULL DEFAULT '5' COMMENT '最大召回数量' AFTER `splitter_type`,
--   ADD COLUMN `retrieval_min_score` double NOT NULL DEFAULT '0.5' COMMENT '最低相似度' AFTER `retrieval_top_k`,
--   ADD COLUMN `index_version` bigint(20) NOT NULL DEFAULT '0' COMMENT '当前索引版本' AFTER `retrieval_min_score`,
--   ADD COLUMN `index_signature` varchar(64) DEFAULT NULL COMMENT '索引配置签名' AFTER `index_version`,
--   ADD COLUMN `index_status` varchar(20) NOT NULL DEFAULT 'EMPTY' COMMENT '索引状态' AFTER `index_signature`,
--   ADD COLUMN `index_error` varchar(1000) DEFAULT NULL COMMENT '最近索引失败原因' AFTER `index_status`;

-- UPDATE `ai_knowledge_base` kb
-- SET kb.`embedding_model_id` = (
--       SELECT mc.id
--       FROM `ai_model_config` mc
--       WHERE mc.`model_type` = 'EMBEDDING'
--         AND mc.`is_default` = '1'
--         AND mc.`status` = '1'
--         AND mc.`del_flag` = '0'
--         AND (mc.`dept_id` IS NULL OR mc.`dept_id` = kb.`dept_id`)
--       ORDER BY CASE
--         WHEN kb.`dept_id` IS NOT NULL AND mc.`dept_id` = kb.`dept_id` THEN 2
--         WHEN mc.`dept_id` IS NULL THEN 1
--         ELSE 0
--       END DESC, mc.id DESC
--       LIMIT 1
--     ),
--     kb.`vector_collection` = 'polaris_knowledge',
--     kb.`index_status` = CASE WHEN EXISTS (
--       SELECT 1 FROM `ai_document` d
--       WHERE d.`knowledge_base_id` = kb.id
--         AND d.`status` = '2'
--         AND d.`del_flag` = '0'
--     ) THEN 'STALE' ELSE 'EMPTY' END,
--     kb.`index_error` = CASE WHEN EXISTS (
--       SELECT 1 FROM `ai_document` d
--       WHERE d.`knowledge_base_id` = kb.id
--         AND d.`status` = '2'
--         AND d.`del_flag` = '0'
--     ) THEN '向量存储配置已升级，请重建知识库索引' ELSE NULL END;

-- ----------------------------------------------------------------------------
-- 【增量 4】AI 敏感内容机器安全检测底座升级
-- ----------------------------------------------------------------------------
-- CREATE TABLE IF NOT EXISTS `ai_moderation_dictionary_version` (
--   `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
--   `version_no` varchar(100) NOT NULL COMMENT '词库版本号',
--   `checksum` char(64) NOT NULL COMMENT '规则规范化SHA-256校验和',
--   `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
--   `source_version` varchar(100) NOT NULL COMMENT '种子来源版本',
--   `source_location` varchar(500) DEFAULT NULL COMMENT '种子资源位置',
--   `published_by` varchar(100) DEFAULT NULL COMMENT '发布或回滚操作人',
--   `published_time` datetime DEFAULT NULL COMMENT '发布时间',
--   `published_slot` tinyint GENERATED ALWAYS AS
--     (CASE WHEN `status` = 'PUBLISHED' THEN 1 ELSE NULL END) STORED COMMENT '唯一已发布槽位',
--   `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
--   `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
--   PRIMARY KEY (`id`),
--   UNIQUE KEY `uk_moderation_version_no` (`version_no`),
--   UNIQUE KEY `uk_moderation_version_checksum` (`checksum`),
--   UNIQUE KEY `uk_moderation_one_published` (`published_slot`),
--   KEY `idx_moderation_version_status` (`status`, `published_time`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感内容词库版本';

-- CREATE TABLE IF NOT EXISTS `ai_moderation_rule` (
--   `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
--   `dictionary_version_id` bigint(20) NOT NULL COMMENT '词库版本ID',
--   `rule_type` varchar(32) NOT NULL COMMENT 'RISK_WORD/RISK_CONTEXT/SAFE_CONTEXT/ALLOW_TERM',
--   `content` varchar(1000) NOT NULL COMMENT '规则原文',
--   `normalized_content` varchar(1000) NOT NULL COMMENT '归一化规则内容',
--   `normalized_hash` char(64) NOT NULL COMMENT '归一化规则内容SHA-256',
--   `category` varchar(64) NOT NULL COMMENT '风险分类',
--   `weight` int(11) NOT NULL DEFAULT '0' COMMENT '风险分权重',
--   `source` varchar(200) DEFAULT NULL COMMENT '规则来源',
--   `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
--   `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
--   PRIMARY KEY (`id`),
--   UNIQUE KEY `uk_moderation_rule_normalized`
--     (`dictionary_version_id`, `rule_type`, `normalized_hash`, `category`),
--   KEY `idx_moderation_rule_version_type` (`dictionary_version_id`, `rule_type`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感内容规则';

-- CREATE TABLE IF NOT EXISTS `ai_moderation_policy` (
--   `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
--   `scene` varchar(32) NOT NULL COMMENT 'KNOWLEDGE/CHAT_INPUT/AI_OUTPUT/WORKFLOW_INPUT/WORKFLOW_OUTPUT',
--   `preset` varchar(20) NOT NULL COMMENT 'LENIENT/BALANCED/STRICT',
--   `mode` varchar(20) NOT NULL DEFAULT 'OBSERVE' COMMENT 'OBSERVE/ENFORCE',
--   `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
--   `suspect_threshold` int(11) DEFAULT 40 COMMENT '疑似阈值',
--   `block_threshold` int(11) DEFAULT 70 COMMENT '阻断阈值',
--   `provider_enabled` tinyint(1) DEFAULT 0 COMMENT '是否启用第三方检测',
--   `provider_timeout_ms` int(11) DEFAULT 1500 COMMENT '第三方检测超时毫秒数',
--   `provider_daily_limit` int(11) DEFAULT 1000 COMMENT '第三方每日调用上限',
--   `provider_monthly_budget` decimal(12,2) DEFAULT 100.00 COMMENT '第三方月度预算',
--   `segment_chars` int(11) DEFAULT 200 COMMENT '分段字符数',
--   `segment_overlap_chars` int(11) DEFAULT 64 COMMENT '分段重叠字符数',
--   `output_buffer_chars` int(11) DEFAULT 300 COMMENT '输出检测缓冲字符数',
--   `quarantine_days` int(11) DEFAULT 7 COMMENT '知识隔离天数',
--   `policy_version` bigint(20) DEFAULT 1 COMMENT '策略版本',
--   `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
--   `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
--   PRIMARY KEY (`id`),
--   UNIQUE KEY `uk_moderation_policy_scene` (`scene`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感内容场景策略';

-- CREATE TABLE IF NOT EXISTS `ai_moderation_event` (
--   `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
--   `request_id` varchar(64) NOT NULL COMMENT '随机检测请求标识',
--   `scene` varchar(32) NOT NULL COMMENT '检测场景',
--   `resource_type` varchar(64) DEFAULT NULL COMMENT '内部资源类型',
--   `resource_id` varchar(128) DEFAULT NULL COMMENT '内部资源标识',
--   `dictionary_version` bigint(20) DEFAULT NULL COMMENT '词库版本ID',
--   `policy_version` bigint(20) DEFAULT NULL COMMENT '策略版本',
--   `local_decision` varchar(20) NOT NULL COMMENT 'PASS/SUSPECT/HIGH_RISK',
--   `final_action` varchar(20) NOT NULL COMMENT 'ALLOW/BLOCK/QUARANTINE/REPLACE',
--   `risk_score` int(11) NOT NULL DEFAULT '0' COMMENT '风险分',
--   `categories` varchar(1000) DEFAULT NULL COMMENT '命中分类列表',
--   `matched_rule_ids` varchar(1000) DEFAULT NULL COMMENT '命中规则ID列表',
--   `provider` varchar(64) DEFAULT NULL COMMENT '第三方提供商',
--   `provider_request_id` varchar(128) DEFAULT NULL COMMENT '第三方请求标识',
--   `provider_decision` varchar(64) DEFAULT NULL COMMENT '第三方结论',
--   `provider_latency_ms` bigint(20) DEFAULT NULL COMMENT '第三方耗时毫秒数',
--   `fallback_reason` varchar(64) DEFAULT NULL COMMENT '降级原因',
--   `content_hash` char(64) NOT NULL COMMENT '待审内容SHA-256，不保存原文',
--   `masked_excerpt` varchar(120) DEFAULT NULL COMMENT '最多120字符的脱敏摘要',
--   `expire_time` datetime NOT NULL COMMENT '审计到期时间',
--   `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
--   PRIMARY KEY (`id`),
--   KEY `idx_moderation_event_request` (`request_id`),
--   KEY `idx_moderation_event_expire` (`expire_time`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感内容检测审计事件';

-- CREATE TABLE IF NOT EXISTS `ai_moderation_candidate` (
--   `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
--   `candidate_term` varchar(256) NOT NULL COMMENT '受控候选词或短表达，不得保存原始待审内容',
--   `expression_hash` char(64) NOT NULL COMMENT '候选表达SHA-256',
--   `masked_excerpt` varchar(120) DEFAULT NULL COMMENT '最多120字符的脱敏证据摘要',
--   `category` varchar(64) NOT NULL COMMENT '建议分类',
--   `source_event_id` bigint(20) DEFAULT NULL COMMENT '来源检测事件ID',
--   `source_signal` varchar(64) NOT NULL COMMENT 'PROVIDER_HIGH_RISK/LOCAL_OBFUSCATION_REPEAT',
--   `observation_count` int(11) NOT NULL DEFAULT 1 COMMENT '已观察次数',
--   `status` varchar(20) NOT NULL DEFAULT 'OBSERVING' COMMENT 'OBSERVING/PENDING/ACCEPTED/REJECTED',
--   `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
--   `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
--   PRIMARY KEY (`id`),
--   UNIQUE KEY `uk_moderation_candidate_expression` (`expression_hash`, `category`),
--   KEY `idx_moderation_candidate_status` (`status`, `update_time`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感内容候选规则';

-- CREATE TABLE IF NOT EXISTS `ai_private_attachment` (
--   `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
--   `attachment_token` varchar(128) NOT NULL COMMENT '私有附件令牌',
--   `user_id` bigint(20) NOT NULL COMMENT '上传用户ID',
--   `original_name` varchar(500) NOT NULL COMMENT '原始文件名',
--   `storage_path` varchar(1000) NOT NULL COMMENT '私有暂存路径',
--   `status` varchar(20) NOT NULL DEFAULT 'WAIT_SCAN' COMMENT 'WAIT_SCAN/SCANNING/SAFE/REJECTED/EXPIRED',
--   `expire_time` datetime NOT NULL COMMENT '到期清理时间',
--   `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
--   `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
--   PRIMARY KEY (`id`),
--   UNIQUE KEY `uk_private_attachment_token` (`attachment_token`),
--   KEY `idx_private_attachment_cleanup` (`status`, `expire_time`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI私有附件暂存';

-- ALTER TABLE `ai_document`
--   ADD COLUMN `moderation_status` varchar(20) NOT NULL DEFAULT 'WAIT_SCAN' COMMENT 'WAIT_SCAN/SCANNING/SAFE/QUARANTINED/SCAN_FAILED/AUTO_DELETED',
--   ADD COLUMN `moderation_event_id` bigint(20) DEFAULT NULL COMMENT '最近检测事件ID',
--   ADD COLUMN `moderation_version` bigint(20) DEFAULT NULL COMMENT '检测使用的词库版本ID',
--   ADD COLUMN `quarantine_path` varchar(1000) DEFAULT NULL COMMENT '隔离文件私有路径',
--   ADD COLUMN `quarantine_expire_time` datetime DEFAULT NULL COMMENT '隔离文件到期时间';

-- ALTER TABLE `ai_message`
--   ADD COLUMN `attachment_tokens` varchar(2000) DEFAULT NULL COMMENT 'AI私有附件令牌列表',
--   ADD COLUMN `moderation_status` varchar(32) DEFAULT NULL COMMENT '安全检测状态：SAFE, INTERRUPTED_BLOCKED',
--   ADD COLUMN `moderation_event_id` bigint(20) DEFAULT NULL COMMENT '安全检测事件ID',
--   ADD COLUMN `moderation_version` bigint(20) DEFAULT NULL COMMENT '生效的词库版本号';

-- ALTER TABLE `ai_workflow_execution`
--   ADD COLUMN `attachment_tokens` varchar(2000) DEFAULT NULL COMMENT 'AI私有附件令牌列表';
