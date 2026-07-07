package com.polaris.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.domain.AiKnowledgeBase;

import java.util.List;

/**
 * AI 知识库服务层接口
 * 
 * @author polaris
 */
public interface IAiKnowledgeService extends IService<AiKnowledgeBase>
{
    // ================================================================
    //  知识库 CRUD
    // ================================================================

    /**
     * 查询知识库列表
     */
    List<AiKnowledgeBase> listKnowledgeBase(AiKnowledgeBase kb);

    /**
     * 根据ID查询知识库
     */
    AiKnowledgeBase selectKnowledgeBaseById(Long id);

    /**
     * 新增知识库
     */
    int insertKnowledgeBase(AiKnowledgeBase kb);

    /**
     * 修改知识库
     */
    int updateKnowledgeBase(AiKnowledgeBase kb);

    /**
     * 删除知识库
     */
    int deleteKnowledgeBase(Long id);

    // ================================================================
    //  文档 CRUD
    // ================================================================

    /**
     * 查询文档列表
     */
    List<AiDocument> listDocument(AiDocument doc);

    /**
     * 根据ID查询文档
     */
    AiDocument selectDocumentById(Long id);

    /**
     * 新增文档
     */
    int insertDocument(AiDocument doc);

    /**
     * 删除文档
     */
    int deleteDocument(Long id);

    // ================================================================
    //  RAG 核心：切片与向量化
    // ================================================================

    /**
     * 异步解析文档并向量化
     */
    void importDocumentAsync(Long docId);
}
