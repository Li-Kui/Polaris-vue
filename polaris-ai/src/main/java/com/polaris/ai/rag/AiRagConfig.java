package com.polaris.ai.rag;

import com.polaris.ai.pivot.AiModelFactory;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * AI RAG 向量知识库配置类
 * 重构为返回具有热切换能力的 EmbeddingModel 动态代理 Bean
 * 
 * @author polaris
 */
@Configuration
public class AiRagConfig
{
    private static final Logger log = LoggerFactory.getLogger(AiRagConfig.class);

    /**
     * 注册具有热切换能力的向量模型代理
     */
    @Bean
    public EmbeddingModel embeddingModel(AiModelFactory factory)
    {
        log.info(">>> 注册 EmbeddingModel 动态热切换代理 Bean");
        return new EmbeddingModel() {
            @Override
            public Response<Embedding> embed(String text) {
                return factory.getEmbeddingModel().embed(text);
            }

            @Override
            public Response<Embedding> embed(TextSegment textSegment) {
                return factory.getEmbeddingModel().embed(textSegment);
            }

            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
                return factory.getEmbeddingModel().embedAll(textSegments);
            }
        };
    }

    /**
     * 注册内存向量数据库 Bean
     * 采用内存存储，服务重启时会重置，但零环境依赖，对于单机开发测试极为便利。
     * 如需生产持久化，可替换为 MilvusEmbeddingStore、PgVectorEmbeddingStore 等实现。
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore()
    {
        log.info(">>> 初始化内存向量数据库 InMemoryEmbeddingStore");
        return new InMemoryEmbeddingStore<>();
    }
}
