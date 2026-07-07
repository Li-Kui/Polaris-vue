package com.polaris.ai.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.ai.attachment.AttachmentParserHelper;
import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.mapper.AiDocumentMapper;
import com.polaris.ai.mapper.AiKnowledgeMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * AI 知识库服务层
 * 
 * @author polaris
 */
@Service
public class AiKnowledgeService
{
    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeService.class);

    @Autowired
    private AiKnowledgeMapper aiKnowledgeMapper;

    @Autowired
    private AiDocumentMapper aiDocumentMapper;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    // ================================================================
    //  知识库 CRUD
    // ================================================================

    public List<AiKnowledgeBase> listKnowledgeBase(AiKnowledgeBase kb)
    {
        return aiKnowledgeMapper.selectKnowledgeBaseList(kb);
    }

    public AiKnowledgeBase selectKnowledgeBaseById(Long id)
    {
        return aiKnowledgeMapper.selectById(id);
    }

    public int insertKnowledgeBase(AiKnowledgeBase kb)
    {
        return aiKnowledgeMapper.insert(kb);
    }

    public int updateKnowledgeBase(AiKnowledgeBase kb)
    {
        return aiKnowledgeMapper.updateById(kb);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteKnowledgeBase(Long id)
    {
        // 1. 删除向量数据库中该知识库下的所有向量分片
        try {
            Filter filter = metadataKey("knowledge_base_id").isEqualTo(id.toString());
            embeddingStore.removeAll(filter);
        } catch (Exception e) {
            log.warn("从向量数据库移除知识库 {} 的数据失败", id, e);
        }

        // 2. 级联逻辑删除数据库中的文档列表与知识库本身
        aiDocumentMapper.delete(new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getKnowledgeBaseId, id));
        return aiKnowledgeMapper.deleteById(id);
    }

    // ================================================================
    //  文档 CRUD
    // ================================================================

    public List<AiDocument> listDocument(AiDocument doc)
    {
        return aiDocumentMapper.selectDocumentList(doc);
    }

    public AiDocument selectDocumentById(Long id)
    {
        return aiDocumentMapper.selectById(id);
    }

    public int insertDocument(AiDocument doc)
    {
        doc.setStatus("0"); // 待解析
        doc.setWordCount(0);
        return aiDocumentMapper.insert(doc);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteDocument(Long id)
    {
        // 1. 从向量库清除该文档的向量
        try {
            Filter filter = metadataKey("document_id").isEqualTo(id.toString());
            embeddingStore.removeAll(filter);
        } catch (Exception e) {
            log.warn("从向量数据库移除文档 {} 的数据失败", id, e);
        }

        // 2. 逻辑删除数据库记录
        return aiDocumentMapper.deleteById(id);
    }

    // ================================================================
    //  RAG 核心：切片与向量化
    // ================================================================

    /**
     * 异步解析文档并向量化
     */
    @Async("threadPoolTaskExecutor")
    public void importDocumentAsync(Long docId)
    {
        AiDocument doc = aiDocumentMapper.selectById(docId);
        if (doc == null) {
            log.error("未找到待导入的文档，ID: {}", docId);
            return;
        }

        log.info(">>> 开始异步解析文档: {}, fileUrl={}", doc.getName(), doc.getFileUrl());
        
        // 1. 更新状态为解析中 (1)
        doc.setStatus("1");
        aiDocumentMapper.updateById(doc);

        try {
            // 2. 提取文本内容
            String content = AttachmentParserHelper.parse(doc.getFileUrl());
            if (content == null || content.trim().isEmpty() || content.startsWith("[解析附件时发生错误")) {
                throw new RuntimeException("文件内容提取为空或解析错误");
            }

            int wordCount = content.length();
            
            // 3. 构造 LangChain4j 的 Document
            Document document = Document.from(content);
            document.metadata().put("knowledge_base_id", doc.getKnowledgeBaseId().toString());
            document.metadata().put("document_id", doc.getId().toString());

            // 4. 切片 (每片 300 字，重叠 30 字)
            DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);
            List<TextSegment> segments = splitter.split(document);

            // 5. 向量化并写入向量库
            log.info(">>> 文档 {} 解析完成，共生成 {} 个分片，开始向量化...", doc.getName(), segments.size());
            for (TextSegment segment : segments) {
                if (segment.text() == null || segment.text().trim().isEmpty()) {
                    continue; // 过滤空分片，避免阿里云百炼等向量模型接口报错 (必须处于 [1, 30720] 范围)
                }
                embeddingStore.add(embeddingModel.embed(segment).content(), segment);
            }

            // 6. 更新状态为已解析 (2)
            doc.setStatus("2");
            doc.setWordCount(wordCount);
            aiDocumentMapper.updateById(doc);
            log.info(">>> 文档 {} 向量化入库成功", doc.getName());

        } catch (Exception e) {
            log.error(">>> 文档 {} 向量化失败", doc.getName(), e);
            doc.setStatus("3"); // 失败
            aiDocumentMapper.updateById(doc);
        }
    }

    /**
     * 监听 Spring 启动就绪事件
     * 由于 InMemoryEmbeddingStore 重启后向量会丢失，此处在每次系统启动后，
     * 自动从数据库找出所有“已解析(2)”的文档，重新读取并向量化加载到内存。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void autoRebuildInMemoryEmbeddings()
    {
        log.info(">>> 检测到系统启动，开始重新构建内存向量数据库...");
        
        AiDocument query = new AiDocument();
        query.setStatus("2"); // 已解析
        List<AiDocument> readyDocs = aiDocumentMapper.selectDocumentList(query);
        
        if (readyDocs == null || readyDocs.isEmpty()) {
            log.info(">>> 没有已解析的文档需要加载到内存向量库");
            return;
        }

        // 异步后台加载，不阻塞 Spring 启动线程
        new Thread(() -> {
            // 提前进行嗅探测试，若向量模型未配置或不可用，则直接退出，不加载内存向量索引
            try {
                embeddingModel.embed("test");
            } catch (Exception e) {
                log.warn(">>> 向量模型不可用（API Key 未正确配置或接口连接失败），跳过后台文档向量索引重建。详细原因: {}", e.getMessage());
                return;
            }

            int successCount = 0;
            for (AiDocument doc : readyDocs) {
                try {
                    String content = AttachmentParserHelper.parse(doc.getFileUrl());
                    if (content == null || content.trim().isEmpty() || content.startsWith("[解析附件时发生错误")) {
                        continue;
                    }
                    Document document = Document.from(content);
                    document.metadata().put("knowledge_base_id", doc.getKnowledgeBaseId().toString());
                    document.metadata().put("document_id", doc.getId().toString());

                    DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);
                    List<TextSegment> segments = splitter.split(document);

                    for (TextSegment segment : segments) {
                        embeddingStore.add(embeddingModel.embed(segment).content(), segment);
                    }
                    successCount++;
                } catch (Exception e) {
                    log.error(">>> 重启加载文档失败: {}", doc.getName(), e);
                }
            }
            log.info(">>> 内存向量数据库重建完毕，成功加载 {}/{} 个文档", successCount, readyDocs.size());
        }).start();
    }
}
