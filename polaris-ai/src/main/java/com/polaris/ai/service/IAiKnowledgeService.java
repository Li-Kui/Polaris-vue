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
     * 根据当前用户数据范围查询知识库。
     */
    AiKnowledgeBase selectAccessibleKnowledgeBaseById(Long id);

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
     * 在新 collection 中重建整个知识库，成功后切换生效版本。
     */
    void rebuildKnowledgeBaseAsync(Long knowledgeBaseId);

    /** 内部 worker 入口：使用已领取的发布代次，不重新领取，不接受控制器直接调用。 */
    void runKnowledgeRebuildTask(com.polaris.ai.knowledge.KnowledgeIndexPublisher.Build build);

    /** 内部增量 worker：父库必须 READY，不能隐式等待或另起全库重建。 */
    void runKnowledgeImportTask(AiKnowledgeBase source, Long documentId);

    /**
     * 标记所有绑定指定向量模型的知识库索引为过期。
     */
    int markIndexesStaleByEmbeddingModelId(Long modelConfigId, String reason);
}
