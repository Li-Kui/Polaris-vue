package com.polaris.ai.rag;

import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.enums.ModelType;
import com.polaris.ai.pivot.AiModelFactory;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the embedding model and vector store that belong to one knowledge base.
 */
@Component
public class AiVectorStoreResolver
{
    private final AiVectorStoreProperties properties;
    private final AiModelFactory modelFactory;
    private final QdrantClient qdrantClient;
    private final Map<String, StoreBinding> stores = new ConcurrentHashMap<>();

    public AiVectorStoreResolver(
            AiVectorStoreProperties properties,
            AiModelFactory modelFactory,
            EmbeddingStore<TextSegment> defaultStore,
            ObjectProvider<QdrantClient> qdrantClientProvider)
    {
        this.properties = properties;
        this.modelFactory = modelFactory;
        this.qdrantClient = qdrantClientProvider.getIfAvailable();
        if (properties.getType() == AiVectorStoreProperties.Type.MEMORY) {
            this.stores.put(properties.getQdrant().getCollectionName(),
                    new StoreBinding(defaultStore, properties.getQdrant().getDimension()));
        }
    }

    public VectorContext resolve(AiKnowledgeBase knowledgeBase)
    {
        if (knowledgeBase == null) {
            throw new IllegalArgumentException("知识库不能为空");
        }
        AiModelConfig modelConfig = resolveModelConfig(knowledgeBase.getEmbeddingModelId());
        String collectionName = hasText(knowledgeBase.getVectorCollection())
                ? knowledgeBase.getVectorCollection().trim()
                : properties.getQdrant().getCollectionName();
        EmbeddingStore<TextSegment> store = resolveStore(collectionName, modelConfig);
        return new VectorContext(
                modelConfig,
                modelFactory.getEmbeddingModel(modelConfig.getId()),
                store,
                collectionName);
    }

    public VectorContext resolveForCollection(AiKnowledgeBase knowledgeBase, String collectionName)
    {
        if (knowledgeBase == null) {
            throw new IllegalArgumentException("知识库不能为空");
        }
        if (!hasText(collectionName)) {
            throw new IllegalArgumentException("向量 collection 不能为空");
        }
        AiModelConfig modelConfig = resolveModelConfig(knowledgeBase.getEmbeddingModelId());
        return new VectorContext(
                modelConfig,
                modelFactory.getEmbeddingModel(modelConfig.getId()),
                resolveStore(collectionName.trim(), modelConfig),
                collectionName.trim());
    }

    /**
     * Resolves an already active collection without requiring the currently configured model.
     * This keeps delete operations working after a model is disabled or its dimension changes.
     */
    public EmbeddingStore<TextSegment> resolveStoreForRemoval(AiKnowledgeBase knowledgeBase)
    {
        if (knowledgeBase == null || !hasText(knowledgeBase.getVectorCollection())) {
            throw new IllegalArgumentException("知识库尚未绑定向量 collection");
        }
        String collectionName = knowledgeBase.getVectorCollection().trim();
        StoreBinding existing = stores.get(collectionName);
        if (existing != null) {
            return existing.store();
        }
        if (properties.getType() == AiVectorStoreProperties.Type.MEMORY) {
            return new InMemoryEmbeddingStore<>();
        }
        if (qdrantClient == null) {
            throw new IllegalStateException("Qdrant 客户端未初始化");
        }
        return buildQdrantStore(collectionName);
    }

    public AiModelConfig resolveModelConfig(Long modelConfigId)
    {
        AiModelConfig config = modelConfigId == null
                ? modelFactory.getDefaultModelConfig(ModelType.EMBEDDING)
                : modelFactory.getModelConfig(modelConfigId);
        if (config == null) {
            throw new IllegalStateException("未配置可用的默认向量模型");
        }
        if (!ModelType.EMBEDDING.name().equalsIgnoreCase(config.getModelType())) {
            throw new IllegalStateException("知识库绑定的模型不是 EMBEDDING 类型: " + config.getId());
        }
        if (!"1".equals(config.getStatus())) {
            throw new IllegalStateException("知识库绑定的向量模型未启用: " + config.getId());
        }
        if (config.getEmbeddingDimension() == null || config.getEmbeddingDimension() <= 0) {
            modelFactory.ensureEmbeddingDimension(config);
        }
        return config;
    }

    public String newVersionCollection(AiKnowledgeBase knowledgeBase, long version)
    {
        AiModelConfig modelConfig = resolveModelConfig(knowledgeBase.getEmbeddingModelId());
        String prefix = properties.getQdrant().getCollectionName().replaceAll("[^A-Za-z0-9_-]", "_");
        return prefix + "_kb" + knowledgeBase.getId()
                + "_m" + modelConfig.getId()
                + "_v" + version;
    }

    private EmbeddingStore<TextSegment> resolveStore(String collectionName, AiModelConfig modelConfig)
    {
        int dimension = modelConfig.getEmbeddingDimension() == null
                ? properties.getQdrant().getDimension()
                : modelConfig.getEmbeddingDimension();
        StoreBinding existing = stores.get(collectionName);
        if (existing != null) {
            if (existing.dimension() != dimension) {
                throw new IllegalStateException(String.format(
                        "向量 collection 已绑定其他维度: collection=%s, expected=%d, actual=%d",
                        collectionName, dimension, existing.dimension()));
            }
            return existing.store();
        }

        StoreBinding created = stores.computeIfAbsent(collectionName, name ->
                new StoreBinding(createStore(name, dimension), dimension));
        if (created.dimension() != dimension) {
            throw new IllegalStateException(String.format(
                    "向量 collection 已绑定其他维度: collection=%s, expected=%d, actual=%d",
                    collectionName, dimension, created.dimension()));
        }
        return created.store();
    }

    private EmbeddingStore<TextSegment> createStore(String collectionName, int dimension)
    {
        if (properties.getType() == AiVectorStoreProperties.Type.MEMORY) {
            return new InMemoryEmbeddingStore<>();
        }
        if (qdrantClient == null) {
            throw new IllegalStateException("Qdrant 客户端未初始化");
        }
        AiVectorStoreProperties.QdrantProperties collectionProperties = copyQdrantProperties();
        collectionProperties.setCollectionName(collectionName);
        collectionProperties.setDimension(dimension);
        QdrantCollectionInitializer.initialize(qdrantClient, collectionProperties);
        return buildQdrantStore(collectionName);
    }

    private EmbeddingStore<TextSegment> buildQdrantStore(String collectionName)
    {
        return QdrantEmbeddingStore.builder()
                .client(qdrantClient)
                .collectionName(collectionName)
                .payloadTextKey(properties.getQdrant().getPayloadTextKey())
                .build();
    }

    private AiVectorStoreProperties.QdrantProperties copyQdrantProperties()
    {
        AiVectorStoreProperties.QdrantProperties source = properties.getQdrant();
        AiVectorStoreProperties.QdrantProperties copy = new AiVectorStoreProperties.QdrantProperties();
        copy.setHost(source.getHost());
        copy.setPort(source.getPort());
        copy.setUseTls(source.isUseTls());
        copy.setApiKey(source.getApiKey());
        copy.setDimension(source.getDimension());
        copy.setOnDisk(source.isOnDisk());
        copy.setInitializeSchema(source.isInitializeSchema());
        copy.setCreatePayloadIndexes(source.isCreatePayloadIndexes());
        copy.setPayloadTextKey(source.getPayloadTextKey());
        copy.setTimeout(source.getTimeout());
        return copy;
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.trim().isEmpty();
    }

    public record VectorContext(
            AiModelConfig modelConfig,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            String collectionName)
    {
    }

    private record StoreBinding(EmbeddingStore<TextSegment> store, int dimension)
    {
    }
}
