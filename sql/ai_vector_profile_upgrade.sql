-- AI 向量模型与知识库索引参数升级

ALTER TABLE `ai_model_config`
  ADD COLUMN `embedding_dimension` int(11) DEFAULT NULL COMMENT '向量模型输出维度' AFTER `model_type`,
  ADD COLUMN `embedding_dimension_mode` varchar(20) DEFAULT 'MODEL_DEFAULT' COMMENT '维度模式(MODEL_DEFAULT/REQUEST)' AFTER `embedding_dimension`,
  ADD COLUMN `embedding_max_input_tokens` int(11) DEFAULT NULL COMMENT '向量模型最大输入Token数' AFTER `embedding_dimension_mode`,
  ADD COLUMN `embedding_batch_size` int(11) DEFAULT '16' COMMENT '向量化批量大小' AFTER `embedding_max_input_tokens`;

UPDATE `ai_model_config`
SET `embedding_dimension` = 1024
WHERE `model_type` = 'EMBEDDING'
  AND `embedding_dimension` IS NULL
  AND `model_name` = 'text-embedding-v3';

ALTER TABLE `ai_knowledge_base`
  ADD COLUMN `embedding_model_id` bigint(20) DEFAULT NULL COMMENT '绑定的向量模型配置ID' AFTER `description`,
  ADD COLUMN `vector_collection` varchar(200) DEFAULT NULL COMMENT '当前生效的向量collection' AFTER `embedding_model_id`,
  ADD COLUMN `chunk_size` int(11) NOT NULL DEFAULT '300' COMMENT '切片最大字符数' AFTER `vector_collection`,
  ADD COLUMN `chunk_overlap` int(11) NOT NULL DEFAULT '30' COMMENT '切片重叠字符数' AFTER `chunk_size`,
  ADD COLUMN `splitter_type` varchar(32) NOT NULL DEFAULT 'RECURSIVE' COMMENT '切片算法' AFTER `chunk_overlap`,
  ADD COLUMN `retrieval_top_k` int(11) NOT NULL DEFAULT '5' COMMENT '最大召回数量' AFTER `splitter_type`,
  ADD COLUMN `retrieval_min_score` double NOT NULL DEFAULT '0.5' COMMENT '最低相似度' AFTER `retrieval_top_k`,
  ADD COLUMN `index_version` bigint(20) NOT NULL DEFAULT '0' COMMENT '当前索引版本' AFTER `retrieval_min_score`,
  ADD COLUMN `index_signature` varchar(64) DEFAULT NULL COMMENT '索引配置签名' AFTER `index_version`,
  ADD COLUMN `index_status` varchar(20) NOT NULL DEFAULT 'EMPTY' COMMENT '索引状态' AFTER `index_signature`,
  ADD COLUMN `index_error` varchar(1000) DEFAULT NULL COMMENT '最近索引失败原因' AFTER `index_status`;

UPDATE `ai_knowledge_base` kb
SET kb.`embedding_model_id` = (
      SELECT mc.id
      FROM `ai_model_config` mc
      WHERE mc.`model_type` = 'EMBEDDING'
        AND mc.`is_default` = '1'
        AND mc.`status` = '1'
        AND mc.`del_flag` = '0'
        AND (mc.`dept_id` IS NULL OR mc.`dept_id` = kb.`dept_id`)
      ORDER BY CASE
        WHEN kb.`dept_id` IS NOT NULL AND mc.`dept_id` = kb.`dept_id` THEN 2
        WHEN mc.`dept_id` IS NULL THEN 1
        ELSE 0
      END DESC, mc.id DESC
      LIMIT 1
    ),
    kb.`vector_collection` = 'polaris_knowledge',
    kb.`index_status` = CASE WHEN EXISTS (
      SELECT 1 FROM `ai_document` d
      WHERE d.`knowledge_base_id` = kb.id
        AND d.`status` = '2'
        AND d.`del_flag` = '0'
    ) THEN 'STALE' ELSE 'EMPTY' END,
    kb.`index_error` = CASE WHEN EXISTS (
      SELECT 1 FROM `ai_document` d
      WHERE d.`knowledge_base_id` = kb.id
        AND d.`status` = '2'
        AND d.`del_flag` = '0'
    ) THEN '向量存储配置已升级，请重建知识库索引' ELSE NULL END;
