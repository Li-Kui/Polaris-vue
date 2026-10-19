-- 协作智能体统一升级脚本，包含工作流协作和普通对话协作。
-- 依赖 01～09，按本文件顺序执行一次；原 10、11 已合并，不再单独保留。
-- 已执行原 10、11 的环境无需执行本文件；本次仅整理脚本，不新增数据库变更。
-- 保留字段说明、索引、约束及 MySQL CHECK 兼容说明，不改业务数据。

-- Workflow collaboration schema
ALTER TABLE ai_agent ADD COLUMN definition_revision bigint NOT NULL DEFAULT 1 COMMENT 'Agent配置与MCP绑定的共同CAS修订';

CREATE TABLE ai_agent_mcp_binding (
 id bigint NOT NULL AUTO_INCREMENT,
 tenant_id bigint NOT NULL,
 agent_id bigint NOT NULL,
 alias varchar(16) NOT NULL,
 tool_id bigint NOT NULL,
 purpose varchar(512) NOT NULL,
 query_argument varchar(64) DEFAULT NULL,
 fixed_arguments_json json NOT NULL COMMENT '管理员显式固定参数，不存本次问题',
 input_schema_json json NOT NULL,
 tool_revision bigint NOT NULL,
 connection_revision bigint NOT NULL,
 catalog_revision bigint NOT NULL,
 structure_hash char(64) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_agent_binding_alias(tenant_id,agent_id,alias),
 UNIQUE KEY uk_agent_binding_tool(tenant_id,agent_id,tool_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent明确审批的MCP只读绑定';

CREATE TABLE ai_agent_run (
 run_id varchar(64) NOT NULL,
 tenant_id bigint NOT NULL,
 actor_user_id bigint NOT NULL,
 agent_id bigint NOT NULL,
 execution_id varchar(64) NOT NULL,
 node_run_id varchar(64) NOT NULL,
 attempt_no int NOT NULL,
 workflow_code varchar(128) NOT NULL,
 runner_id varchar(128) NOT NULL,
 fencing_token bigint NOT NULL,
 definition_hash char(64) NOT NULL,
 model_id bigint NOT NULL,
 model_hash char(64) NOT NULL,
 request_hash char(64) NOT NULL,
 status varchar(16) NOT NULL,
 stage varchar(16) DEFAULT NULL,
 max_calls int NOT NULL,
 tool_count int NOT NULL DEFAULT 0,
 plan_count int NOT NULL DEFAULT 0,
 answer_count int NOT NULL DEFAULT 0,
 plan_usage_recorded boolean NOT NULL DEFAULT false,
 answer_usage_recorded boolean NOT NULL DEFAULT false,
 plan_input_tokens bigint DEFAULT NULL,
 plan_output_tokens bigint DEFAULT NULL,
 plan_total_tokens bigint DEFAULT NULL,
 answer_input_tokens bigint DEFAULT NULL,
 answer_output_tokens bigint DEFAULT NULL,
 answer_total_tokens bigint DEFAULT NULL,
 error_code varchar(32) DEFAULT NULL,
 dispatch varchar(16) NOT NULL DEFAULT 'NOT_DISPATCHED',
 deadline datetime(3) NOT NULL,
 started_time datetime(3) NOT NULL,
 finished_time datetime(3) DEFAULT NULL,
 PRIMARY KEY(run_id),
 UNIQUE KEY uk_agent_run_attempt(tenant_id,execution_id,node_run_id,attempt_no),
 KEY idx_agent_run_execution(tenant_id,execution_id,started_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='有界协作运行台账，不是独立任务引擎';

CREATE TABLE ai_agent_run_call (
 tenant_id bigint NOT NULL,
 run_id varchar(64) NOT NULL,
 call_no int NOT NULL,
 tool_id bigint NOT NULL,
 tool_revision bigint NOT NULL,
 connection_revision bigint NOT NULL,
 catalog_revision bigint NOT NULL,
 structure_hash char(64) NOT NULL,
 arguments_hash char(64) NOT NULL COMMENT '规范化参数摘要，原文不保存',
 status varchar(16) NOT NULL DEFAULT 'RESERVED',
 invocation_id char(36) DEFAULT NULL,
 dispatch varchar(16) NOT NULL DEFAULT 'NOT_DISPATCHED',
 PRIMARY KEY(tenant_id,run_id,call_no),
 UNIQUE KEY uk_agent_call_invocation(tenant_id,invocation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='原子预留调用预算和序号，仅元数据';

ALTER TABLE platform_mcp_invocation
 ADD COLUMN agent_run_id varchar(64) DEFAULT NULL COMMENT '协作Agent运行ID',
 ADD COLUMN agent_call_no int NOT NULL DEFAULT 0 COMMENT '普通调用0，Agent调用1或2',
 MODIFY COLUMN source varchar(16) NOT NULL COMMENT 'MANUAL/WORKFLOW/AGENT_WORKFLOW',
 DROP INDEX uk_mcp_workflow_attempt,
 ADD UNIQUE KEY uk_mcp_workflow_attempt(tenant_id,execution_id,node_run_id,attempt_no,agent_call_no),
 ADD UNIQUE KEY uk_mcp_agent_call(tenant_id,agent_run_id,agent_call_no);

-- Chat collaboration schema
-- MySQL 5.7 只解析而不执行 CHECK，入口/预算/状态校验仍必须由服务端强制完成。
-- MySQL 8.0.16+ 同时使用 CHECK 防御，不能把 CHECK 当成唯一授权或围栏。
CREATE TABLE ai_agent_chat_request (
 request_id char(36) NOT NULL COMMENT '聊天协作请求ID，同时作为本轮确认ID',
 tenant_id bigint NOT NULL COMMENT '所属租户ID',
 actor_user_id bigint NOT NULL COMMENT '发起请求的当前租户用户ID',
 conversation_id bigint NOT NULL COMMENT '所属对话会话ID，关联ai_conversation.id',
 request_key varchar(64) NOT NULL COMMENT '客户端幂等键，在租户、用户和会话范围内唯一',
 input_hash char(64) NOT NULL COMMENT '本轮输入及策略版本的SHA-256摘要，用于拒绝同键不同内容',
 snapshot_hash char(64) NOT NULL COMMENT '资源版本与授权范围快照的SHA-256摘要',
 snapshot_json json NOT NULL COMMENT '资源版本与授权范围元数据，不含角色、问题、查询或载荷正文',
 history_hash char(64) NOT NULL COMMENT '历史上下文快照的SHA-256摘要，用于检测确认后历史变化',
 history_json json NOT NULL COMMENT '历史消息ID、顺序、正文摘要与来源指针，不复制正文',
 context_key char(64) NOT NULL COMMENT 'Agent、模型定义及知识库选择生成的上下文段摘要',
 context_start_message_id bigint NOT NULL COMMENT '本轮历史起始消息ID，包含该ID，0表示不额外限制起点',
 history_watermark bigint NOT NULL COMMENT '确认时会话最新消息ID，无消息时为0',
 answer_mode varchar(24) NOT NULL COMMENT '回答模式：QUERY_EVIDENCE查询资料，CONTINUE_HISTORY基于前文',
 agent_id bigint NOT NULL COMMENT '本轮选定的智能体ID，关联ai_agent.id',
 model_id bigint NOT NULL COMMENT '智能体绑定的回答模型配置ID，关联ai_model_config.id',
 knowledge_base_id bigint DEFAULT NULL COMMENT '本轮选定的知识库ID，未选择时为空',
 max_calls int NOT NULL COMMENT '本轮MCP工具调用次数上限，范围0至2',
 max_wait_seconds int NOT NULL COMMENT '本轮执行总时限，单位秒，上限180秒',
 status varchar(24) NOT NULL DEFAULT 'PREPARED' COMMENT '请求状态：PREPARED待执行，RUNNING执行中，SUCCEEDED成功，FAILED失败，CANCELLED取消，NEEDS_ATTENTION待核对，EXPIRED确认过期',
 run_id varchar(64) DEFAULT NULL COMMENT '领取后生成的协作运行ID，关联ai_agent_run.run_id',
 user_message_id bigint DEFAULT NULL COMMENT '本轮已保存的用户消息ID，关联ai_message.id',
 assistant_message_id bigint DEFAULT NULL COMMENT '本轮已保存的助手消息ID，关联ai_message.id',
 owner_token char(36) DEFAULT NULL COMMENT '本轮执行持有者随机令牌，用于领取、续约和结束的CAS围栏',
 lease_until datetime(3) DEFAULT NULL COMMENT '当前执行租约截止时间，精确到毫秒',
 cancel_requested_at datetime(3) DEFAULT NULL COMMENT '取消请求持久受理时间，不代表远端调用已取消',
 expires_at datetime(3) NOT NULL COMMENT '本轮确认有效期截止时间，过期后不得首次领取',
 deadline datetime(3) DEFAULT NULL COMMENT '领取后确定的本轮执行绝对截止时间',
 error_code varchar(32) DEFAULT NULL COMMENT '安全错误码，不保存供应商异常或业务正文',
 created_time datetime(3) NOT NULL COMMENT '请求准备记录创建时间',
 started_time datetime(3) DEFAULT NULL COMMENT '首次成功领取并开始执行的时间',
 finished_time datetime(3) DEFAULT NULL COMMENT '请求进入成功、失败、取消、待核对或过期终态的时间',
 PRIMARY KEY(request_id),
 UNIQUE KEY uk_agent_chat_key(tenant_id,actor_user_id,conversation_id,request_key),
 UNIQUE KEY uk_agent_chat_run(tenant_id,run_id),
 KEY idx_agent_chat_conversation(tenant_id,actor_user_id,conversation_id,created_time),
 CONSTRAINT chk_agent_chat_mode CHECK (answer_mode IN ('QUERY_EVIDENCE','CONTINUE_HISTORY')),
 CONSTRAINT chk_agent_chat_budget CHECK (max_calls BETWEEN 0 AND 2 AND max_wait_seconds BETWEEN 1 AND 180),
 CONSTRAINT chk_agent_chat_status CHECK (status IN ('PREPARED','RUNNING','SUCCEEDED','FAILED','CANCELLED','NEEDS_ATTENTION','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='聊天单轮确认、幂等与租约，无自动恢复';

CREATE TABLE ai_chat_active_generation (
 tenant_id bigint NOT NULL COMMENT '所属租户ID',
 actor_user_id bigint NOT NULL COMMENT '当前会话持有用户ID',
 conversation_id bigint NOT NULL COMMENT '占用的对话会话ID，关联ai_conversation.id',
 entry_type varchar(16) NOT NULL COMMENT '生成入口：DIRECT_CHAT普通聊天，AGENT_CHAT智能体协作聊天',
 generation_id varchar(64) NOT NULL COMMENT '本次生成唯一ID，AGENT_CHAT时为聊天协作请求ID',
 owner_token char(36) NOT NULL COMMENT '本次生成持有者随机令牌，用于续约及精确释放占用',
 lease_until datetime(3) NOT NULL COMMENT '会话占用租约截止时间，过期后禁止原执行继续',
 deadline datetime(3) NOT NULL COMMENT '本次生成绝对截止时间，续约不得超过该时间',
 cancel_requested_at datetime(3) DEFAULT NULL COMMENT '本次生成的持久取消请求时间，空表示未受理取消',
 PRIMARY KEY(tenant_id,actor_user_id,conversation_id),
 UNIQUE KEY uk_chat_generation(tenant_id,generation_id),
 CONSTRAINT chk_chat_generation_entry CHECK (entry_type IN ('DIRECT_CHAT','AGENT_CHAT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='会话生成互斥与围栏，只允许原持有者继续';

ALTER TABLE ai_conversation
 ADD COLUMN collaboration_context_key char(64) DEFAULT NULL COMMENT '当前协作上下文段摘要，首次领取协作请求时设置',
 ADD COLUMN collaboration_context_start_id bigint NOT NULL DEFAULT 0 COMMENT '当前协作历史起始消息ID，0表示不额外限制起点';

ALTER TABLE ai_message
 ADD COLUMN agent_run_id varchar(64) DEFAULT NULL COMMENT '所属协作运行ID，非协作消息为空',
 ADD COLUMN chat_context_key char(64) DEFAULT NULL COMMENT '消息保存时的协作上下文段摘要',
 ADD COLUMN history_dependencies json DEFAULT NULL COMMENT '版本化传递来源指针，非正文或授权缓存',
 ADD UNIQUE KEY uk_message_agent_role(conversation_id,agent_run_id,role);

ALTER TABLE ai_agent_run
 ADD COLUMN entry_type varchar(16) NOT NULL DEFAULT 'WORKFLOW' COMMENT '运行入口：WORKFLOW工作流，CHAT普通对话协作',
 ADD COLUMN chat_request_id char(36) DEFAULT NULL COMMENT '聊天协作请求ID，WORKFLOW入口为空',
 ADD COLUMN conversation_id bigint DEFAULT NULL COMMENT '聊天所属会话ID，WORKFLOW入口为空',
 MODIFY COLUMN execution_id varchar(64) DEFAULT NULL COMMENT '真实工作流执行ID，CHAT入口为空',
 MODIFY COLUMN node_run_id varchar(64) DEFAULT NULL COMMENT '真实工作流节点运行ID，CHAT入口为空',
 MODIFY COLUMN attempt_no int DEFAULT NULL COMMENT '工作流节点尝试号，首版固定为1，CHAT入口为空',
 MODIFY COLUMN workflow_code varchar(128) DEFAULT NULL COMMENT '所属工作流编码，CHAT入口为空',
 MODIFY COLUMN runner_id varchar(128) DEFAULT NULL COMMENT '工作流运行持有者标识，CHAT入口为空',
 MODIFY COLUMN fencing_token bigint DEFAULT NULL COMMENT '工作流执行围栏令牌，CHAT入口为空，聊天围栏由请求租约提供',
 ADD UNIQUE KEY uk_agent_run_chat(tenant_id,chat_request_id),
 ADD CONSTRAINT chk_agent_run_entry CHECK (
  (entry_type='WORKFLOW' AND execution_id IS NOT NULL AND node_run_id IS NOT NULL
   AND attempt_no IS NOT NULL AND attempt_no=1 AND workflow_code IS NOT NULL AND runner_id IS NOT NULL
   AND fencing_token IS NOT NULL AND chat_request_id IS NULL AND conversation_id IS NULL)
  OR (entry_type='CHAT' AND chat_request_id IS NOT NULL AND conversation_id IS NOT NULL
   AND execution_id IS NULL AND node_run_id IS NULL AND attempt_no IS NULL
   AND workflow_code IS NULL AND runner_id IS NULL AND fencing_token IS NULL)
 );

ALTER TABLE platform_mcp_invocation
 ADD COLUMN chat_request_id char(36) DEFAULT NULL COMMENT 'AGENT_CHAT来源的聊天协作请求ID，其他来源为空',
 ADD COLUMN conversation_id bigint DEFAULT NULL COMMENT 'AGENT_CHAT来源的对话会话ID，其他来源为空',
 MODIFY COLUMN source varchar(16) NOT NULL COMMENT '调用来源：MANUAL手工，WORKFLOW工作流，AGENT_WORKFLOW工作流协作，AGENT_CHAT聊天协作',
 ADD CONSTRAINT chk_mcp_chat_source CHECK (
  (source='AGENT_CHAT' AND chat_request_id IS NOT NULL AND conversation_id IS NOT NULL
   AND agent_run_id IS NOT NULL AND agent_call_no BETWEEN 1 AND 2
   AND execution_id IS NULL AND node_run_id IS NULL AND attempt_no IS NULL)
  OR (source<>'AGENT_CHAT' AND chat_request_id IS NULL AND conversation_id IS NULL)
 );
