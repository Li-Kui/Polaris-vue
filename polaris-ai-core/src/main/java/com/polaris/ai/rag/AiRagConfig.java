package com.polaris.ai.rag;

import com.polaris.ai.pivot.AiModelFactory;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * AI RAG 向量知识库配置类
 * 重构为返回具有热切换能力的 EmbeddingModel 动态代理 Bean
 * 
 * @author polaris
 */
@Configuration
@EnableConfigurationProperties(AiVectorStoreProperties.class)
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
        return new HotSwappableEmbeddingModel(factory::getEmbeddingModel);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.vector-store", name = "type", havingValue = "memory", matchIfMissing = true)
    public EmbeddingStore<TextSegment> inMemoryEmbeddingStore()
    {
        log.info(">>> 初始化内存向量数据库 InMemoryEmbeddingStore");
        return new InMemoryEmbeddingStore<>();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(QdrantClient.class)
    @ConditionalOnProperty(prefix = "ai.vector-store", name = "type", havingValue = "qdrant")
    public QdrantClient qdrantClient(AiVectorStoreProperties properties)
    {
        AiVectorStoreProperties.QdrantProperties qdrant = properties.getQdrant();
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                        qdrant.getHost(), qdrant.getPort(), qdrant.isUseTls())
                .withTimeout(qdrant.getTimeout());
        if (StringUtils.hasText(qdrant.getApiKey())) {
            builder.withApiKey(qdrant.getApiKey().trim());
        }

        // 集合结构取决于知识库绑定的向量模型，待模型明确后由解析器延迟初始化。
        return new QdrantClient(builder.build());
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.vector-store", name = "type", havingValue = "qdrant")
    public EmbeddingStore<TextSegment> qdrantEmbeddingStore(
            QdrantClient client,
            AiVectorStoreProperties properties)
    {
        AiVectorStoreProperties.QdrantProperties qdrant = properties.getQdrant();
        log.info(">>> 初始化 Qdrant 向量数据库, collection={}", qdrant.getCollectionName());
        return QdrantEmbeddingStore.builder()
                .client(client)
                .collectionName(qdrant.getCollectionName())
                .payloadTextKey(qdrant.getPayloadTextKey())
                .build();
    }
}
