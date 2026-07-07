-- ----------------------------
-- Table structure for ai_agent
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent`;
CREATE TABLE `ai_agent` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_code` varchar(50) NOT NULL COMMENT '智能体唯一编码(用于引擎检索)',
  `agent_name` varchar(100) NOT NULL COMMENT '智能体名称',
  `model_name` varchar(100) NOT NULL DEFAULT 'deepseek-chat' COMMENT '底座大模型名称',
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
-- Table structure for ai_workflow
-- ----------------------------
DROP TABLE IF EXISTS `ai_workflow`;
CREATE TABLE `ai_workflow` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `workflow_code` varchar(50) NOT NULL COMMENT '工作流唯一编码',
  `workflow_name` varchar(100) NOT NULL COMMENT '工作流名称',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `nodes` text NOT NULL COMMENT '流程节点编排JSON (如: ["intent_route", "sys_user_analyst", "reporter"])',
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
-- 初始化默认测试数据
-- ----------------------------
INSERT INTO `ai_agent` (agent_code, agent_name, model_name, system_prompt, temperature, tools, status, create_by, create_time)
VALUES 
('intent_router', '意图分发员', 'deepseek-chat', '你是一个路由分发员。请分析用户的意图，决定是要查询系统用户、搜索全网还是普通闲聊。', 0.1, '', '1', 'admin', NOW()),
('sys_user_analyst', '系统用户审计师', 'deepseek-chat', '你是一个严谨的系统用户分析审计专家。你可以通过调用提供的系统工具 queryUserList 获取当前系统里的用户，并基于检索到的数据，写出一份系统安全评估与分析报告。', 0.2, 'SysUserTools', '1', 'admin', NOW());

INSERT INTO `ai_workflow` (workflow_code, workflow_name, description, nodes, status, create_by, create_time)
VALUES 
('sys_user_audit', '系统用户安全审计流', '全自动系统用户检索、数据安全分析的一键式流程', '["intent_router", "sys_user_analyst"]', '1', 'admin', NOW());

-- ----------------------------
-- 注册系统后台管理菜单
-- ----------------------------

