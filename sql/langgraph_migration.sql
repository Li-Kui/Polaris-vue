-- ==================================================================
-- LangGraph4j 整合 - 数据库迁移脚本
-- 执行环境：MySQL 8.0+
-- ==================================================================

-- 1. ai_workflow 表新增 graph_json 字段（核心）
ALTER TABLE ai_workflow ADD COLUMN graph_json TEXT COMMENT '图拓扑描述JSON（统一格式）' AFTER nodes;

-- 2. 新建图工作流执行检查点表（一期建表、二期启用）
CREATE TABLE IF NOT EXISTS ai_graph_checkpoint (
    id                    BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    thread_id             VARCHAR(128) NOT NULL COMMENT '会话线程ID',
    workflow_code         VARCHAR(128) NOT NULL COMMENT '工作流编码',
    checkpoint_id         VARCHAR(128) NOT NULL COMMENT 'checkpoint唯一ID',
    parent_checkpoint_id  VARCHAR(128) COMMENT '父checkpoint ID',
    state_json            LONGTEXT     NOT NULL COMMENT '序列化的AgentState纯数据JSON',
    metadata_json         TEXT         COMMENT 'checkpoint元数据JSON',
    writes_json           TEXT         COMMENT '中间写入操作JSON',
    status                VARCHAR(20)  DEFAULT 'running' COMMENT '状态：running/paused/done/error',
    create_time           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_thread_checkpoint (thread_id, checkpoint_id),
    INDEX idx_thread_status (thread_id, status),
    INDEX idx_workflow_code (workflow_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图工作流执行检查点';
