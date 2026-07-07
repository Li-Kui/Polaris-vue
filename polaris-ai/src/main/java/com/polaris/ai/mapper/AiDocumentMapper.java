package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.domain.AiDocument;

import java.util.List;

/**
 * AI 知识库文档明细数据访问层
 * 
 * @author polaris
 */
public interface AiDocumentMapper extends BaseMapper<AiDocument>
{
    /**
     * 查询文档列表
     */
    List<AiDocument> selectDocumentList(AiDocument document);
}
