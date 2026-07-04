package com.polaris.ai.mapper;

import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.common.annotation.DataScope;

import java.util.List;

/**
 * AI 知识库与文档管理数据访问层
 * 
 * @author polaris
 */
public interface AiKnowledgeMapper
{
    // ================================================================
    //  知识库主表操作
    // ================================================================

    /**
     * 新增知识库
     */
    int insertKnowledgeBase(AiKnowledgeBase knowledgeBase);

    /**
     * 修改知识库信息
     */
    int updateKnowledgeBase(AiKnowledgeBase knowledgeBase);

    /**
     * 删除知识库
     */
    int deleteKnowledgeBase(Long id);

    /**
     * 查询知识库列表
     */
    @DataScope(deptAlias = "kb")
    List<AiKnowledgeBase> selectKnowledgeBaseList(AiKnowledgeBase knowledgeBase);

    /**
     * 根据 ID 获取知识库详情
     */
    AiKnowledgeBase selectKnowledgeBaseById(Long id);

    // ================================================================
    //  文档明细表操作
    // ================================================================

    /**
     * 新增文档
     */
    int insertDocument(AiDocument document);

    /**
     * 更新文档解析状态、字数等信息
     */
    int updateDocument(AiDocument document);

    /**
     * 删除文档
     */
    int deleteDocument(Long id);

    /**
     * 级联删除指定知识库下的所有文档
     */
    int deleteDocumentsByKnowledgeBaseId(Long knowledgeBaseId);

    /**
     * 查询文档列表
     */
    List<AiDocument> selectDocumentList(AiDocument document);

    /**
     * 根据 ID 获取文档详情
     */
    AiDocument selectDocumentById(Long id);
}
