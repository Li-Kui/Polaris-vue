-- 知识库质量升级合并脚本：原 SQL 21～30，按原依赖顺序保留全部 DDL。
-- 前置：已执行项目基础建表脚本，ai_knowledge_base、ai_document、ai_message 已存在。
-- 仅用于尚未执行原 21～30 的数据库，整份执行一次；已全部执行的环境无需再执行。
-- 如果只执行过部分旧脚本，请核对结构后仅补对应分段，禁止直接重复运行整份脚本。
-- 本文件不是可重复执行脚本；MySQL DDL 会隐式提交，中途失败须核对已成功的分段后再继续。
-- 仅建表、加字段及索引；不删除数据、不重建索引、不调用模型、不自动派发任务。
-- 不修改基础脚本或恢复已删除的内部应用 SQL 11～20。新环境完成本文件后再启动新后端。

-- BEGIN 21_knowledge_document_snapshot.sql
CREATE TABLE `ai_document_index_snapshot` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `snapshot_key` char(36) NOT NULL COMMENT '服务端生成的私有载荷文件标识',
    `tenant_id` bigint DEFAULT NULL COMMENT '从父知识库派生，管理端共享库可为空',
    `knowledge_base_id` bigint NOT NULL,
    `document_id` bigint NOT NULL,
    `index_version` bigint NOT NULL,
    `collection_hash` char(64) NOT NULL COMMENT '实际写入collection摘要，非可访问地址',
    `snapshot_state` varchar(16) NOT NULL COMMENT 'PREPARED/READY/FAILED/RETIRED；READY不等于索引已激活',
    `file_hash` char(64) DEFAULT NULL COMMENT '解析失败时可能未知',
    `content_hash` char(64) DEFAULT NULL,
    `payload_hash` char(64) DEFAULT NULL,
    `parser_version` varchar(64) NOT NULL,
    `splitter_version` varchar(64) NOT NULL,
    `parsed_chars` int NOT NULL,
    `chunk_count` int NOT NULL,
    `warnings_json` varchar(2000) NOT NULL,
    `error_code` varchar(64) DEFAULT NULL COMMENT '固定安全错误码，不保存异常原文',
    `create_time` datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_document_snapshot_key` (`snapshot_key`),
    KEY `idx_document_snapshot_current` (`knowledge_base_id`, `document_id`, `index_version`, `collection_hash`, `id`),
    KEY `idx_document_snapshot_tenant` (`tenant_id`, `knowledge_base_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档实际入库快照元数据';
-- END 21_knowledge_document_snapshot.sql

-- BEGIN 22_knowledge_index_generation.sql
ALTER TABLE `ai_knowledge_base`
    ADD COLUMN `index_generation` bigint NOT NULL DEFAULT 0
    COMMENT '服务端索引发布代次；构建、配置或资料变更使旧候选失效';
-- END 22_knowledge_index_generation.sql

-- BEGIN 23_knowledge_rebuild_task.sql
CREATE TABLE `ai_knowledge_rebuild_task` (
    `task_id` varchar(36) NOT NULL,
    `tenant_id` bigint DEFAULT NULL,
    `knowledge_base_id` bigint NOT NULL,
    `active_knowledge_id` bigint DEFAULT NULL COMMENT '活动时为父库ID；终态置空以释放唯一槽',
    `request_key` varchar(64) NOT NULL COMMENT '同库幂等提交键',
    `source_generation` bigint NOT NULL,
    `claimed_generation` bigint DEFAULT NULL,
    `task_state` varchar(24) NOT NULL,
    `owner_token` varchar(36) DEFAULT NULL,
    `lease_until` datetime(3) NOT NULL,
    `document_ids_json` text DEFAULT NULL COMMENT '仅ID清单，不保存正文或私有路径；不用于断点续跑',
    `total_documents` int NOT NULL DEFAULT 0,
    `completed_documents` int NOT NULL DEFAULT 0,
    `error_code` varchar(48) DEFAULT NULL,
    `create_by` varchar(128) NOT NULL,
    `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `start_time` datetime(3) DEFAULT NULL,
    `finish_time` datetime(3) DEFAULT NULL,
    PRIMARY KEY (`task_id`),
    UNIQUE KEY `uk_knowledge_rebuild_active` (`active_knowledge_id`),
    UNIQUE KEY `uk_knowledge_rebuild_request` (`knowledge_base_id`, `request_key`),
    KEY `idx_knowledge_rebuild_history` (`knowledge_base_id`, `create_time`),
    KEY `idx_knowledge_rebuild_tenant` (`tenant_id`, `knowledge_base_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库手动重建任务；过期后仅显式重新构建';
-- END 23_knowledge_rebuild_task.sql

-- BEGIN 24_knowledge_document_worker.sql
ALTER TABLE `ai_document`
    ADD COLUMN `index_owner_token` varchar(36) DEFAULT NULL COMMENT '内部文档worker所有者；不通过接口暴露',
    ADD COLUMN `index_parent_generation` bigint DEFAULT NULL COMMENT '领取时父库发布代次',
    ADD COLUMN `index_lease_until` datetime(3) DEFAULT NULL COMMENT '数据库时钟文档处理租约';
-- END 24_knowledge_document_worker.sql

-- BEGIN 25_knowledge_import_task.sql
ALTER TABLE `ai_knowledge_rebuild_task`
    ADD COLUMN `task_type` varchar(16) NOT NULL DEFAULT 'REBUILD' COMMENT 'REBUILD手动；IMPORT上传；STARTUP启动',
    ADD COLUMN `document_id` bigint DEFAULT NULL COMMENT '上传关联的文档ID；不是客户端指定来源';
-- END 25_knowledge_import_task.sql

-- BEGIN 26_knowledge_snapshot_cleanup.sql
ALTER TABLE `ai_document_index_snapshot`
    ADD COLUMN `payload_cleanup_state` varchar(24) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/RETRY_WAIT/SUCCEEDED/MANUAL_REQUIRED',
    ADD COLUMN `payload_cleanup_attempts` int NOT NULL DEFAULT 0,
    ADD COLUMN `payload_cleanup_error` varchar(48) DEFAULT NULL COMMENT '固定清理错误码，不覆盖原解析错误',
    ADD COLUMN `payload_cleanup_after` datetime(3) DEFAULT NULL COMMENT '数据库时钟下次可清理时间';
-- END 26_knowledge_snapshot_cleanup.sql

-- BEGIN 27_knowledge_vector_collection_registry.sql
CREATE TABLE `ai_knowledge_vector_collection` (
    `registry_id` varchar(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `tenant_id` bigint DEFAULT NULL,
    `knowledge_base_id` bigint NOT NULL,
    `build_generation` bigint NOT NULL,
    `index_version` bigint NOT NULL,
    `backend_type` varchar(16) NOT NULL,
    `backend_key` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务端存储路由摘要；不是远端集群身份凭证',
    `collection_name` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `registry_state` varchar(24) NOT NULL COMMENT 'RESERVED/CREATING/WRITING/BUILT/ACTIVE/RETIRED/ABANDONED/UNKNOWN',
    `creation_acknowledged` tinyint NOT NULL DEFAULT 0 COMMENT '仅全新创建及初始化成功返回后置1；不自动认领同名历史集合',
    `error_code` varchar(48) DEFAULT NULL,
    `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `retire_time` datetime(3) DEFAULT NULL,
    PRIMARY KEY (`registry_id`),
    UNIQUE KEY `uk_knowledge_vector_generation` (`knowledge_base_id`, `build_generation`),
    UNIQUE KEY `uk_knowledge_vector_route_name` (`backend_key`, `collection_name`),
    KEY `idx_knowledge_vector_history` (`tenant_id`, `knowledge_base_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库新候选向量集合登记；本批无物理删除';
-- END 27_knowledge_vector_collection_registry.sql

-- BEGIN 28_knowledge_vector_ownership.sql
ALTER TABLE `ai_knowledge_vector_collection`
    ADD COLUMN `ownership_version` int NOT NULL DEFAULT 0 COMMENT '0=历史未标记，1=创建时写入随机标记',
    ADD COLUMN `ownership_marker` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '内部随机证据；不是访问凭证或不可变集群ID';
-- END 28_knowledge_vector_ownership.sql

-- BEGIN 29_knowledge_vector_cleanup.sql
ALTER TABLE `ai_knowledge_vector_collection`
    ADD COLUMN `cleanup_state` varchar(24) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/RUNNING/RETRY_WAIT/SUCCEEDED/MANUAL_REQUIRED',
    ADD COLUMN `cleanup_attempts` int NOT NULL DEFAULT 0,
    ADD COLUMN `cleanup_requested_by` bigint DEFAULT NULL COMMENT '最近一次显式领取的真实用户ID',
    ADD COLUMN `cleanup_requested_at` datetime(3) DEFAULT NULL,
    ADD COLUMN `cleanup_owner_token` varchar(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    ADD COLUMN `cleanup_plan_key` varchar(78) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    ADD COLUMN `cleanup_lease_until` datetime(3) DEFAULT NULL,
    ADD COLUMN `cleanup_after` datetime(3) DEFAULT NULL,
    ADD COLUMN `cleanup_error` varchar(48) DEFAULT NULL,
    ADD COLUMN `cleanup_dispatched` tinyint NOT NULL DEFAULT 0 COMMENT '派发前独立提交；1不代表远端已执行或删除成功',
    ADD COLUMN `cleanup_confirmed_at` datetime(3) DEFAULT NULL;
-- END 29_knowledge_vector_cleanup.sql

-- BEGIN 30_chat_knowledge_citations.sql
ALTER TABLE `ai_message`
    ADD COLUMN `knowledge_citations` mediumtext DEFAULT NULL COMMENT '服务端生成的知识引用指针JSON；不保存正文或路径';
-- END 30_chat_knowledge_citations.sql
