-- ----------------------------------------------------------------
-- 1. 表结构 DDL 升级：新增大模型配置物理外键 model_config_id
-- ----------------------------------------------------------------

-- 会话表（ai_conversation）增加大模型配置外键 ID
ALTER TABLE ai_conversation ADD COLUMN model_config_id BIGINT DEFAULT NULL COMMENT '所选大模型配置ID';

-- 智能体表（ai_agent）增加大模型配置外键 ID
ALTER TABLE ai_agent ADD COLUMN model_config_id BIGINT DEFAULT NULL COMMENT '所选大模型配置ID';

-- 模型配置表（ai_model_config）增加备注字段
ALTER TABLE ai_model_config ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT '备注';

-- ----------------------------------------------------------------
-- 2. 历史数据 DML 刷数迁移：根据旧的模型名关联匹配已启用的模型配置主键并补充 ID
-- ----------------------------------------------------------------

-- 迁移会话历史数据
UPDATE ai_conversation c SET c.model_config_id = (
    SELECT m.id FROM ai_model_config m 
    WHERE m.model_name = c.model AND m.status = '1' AND m.del_flag = '0'
    LIMIT 1
) WHERE c.model IS NOT NULL AND c.model_config_id IS NULL;

-- 迁移智能体历史数据
UPDATE ai_agent a SET a.model_config_id = (
    SELECT m.id FROM ai_model_config m 
    WHERE m.model_name = a.model_name AND m.status = '1' AND m.del_flag = '0'
    LIMIT 1
) WHERE a.model_name IS NOT NULL AND a.model_config_id IS NULL;
