package com.polaris.ai.rag;

import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.observability.ObservedEmbeddingStore;
import com.polaris.ai.runtime.embedding.EmbeddingRuntimeBinding;
import com.polaris.ai.runtime.embedding.EmbeddingRuntimeDescriptor;
import com.polaris.ai.runtime.embedding.EmbeddingRuntimeGateway;
import com.polaris.common.exception.ServiceException;
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
    private final EmbeddingRuntimeGateway runtimeGateway;
    private final QdrantClient qdrantClient;
    private final Map<String, StoreBinding> stores = new ConcurrentHashMap<>();

    public AiVectorStoreResolver(
            AiVectorStoreProperties properties,
            EmbeddingRuntimeGateway runtimeGateway,
            EmbeddingStore<TextSegment> defaultStore,
            ObjectProvider<QdrantClient> qdrantClientProvider)
    {
        this.properties = properties;
        this.runtimeGateway = runtimeGateway;
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
        EmbeddingRuntimeBinding binding = resolveBinding(knowledgeBase);
        String collectionName = hasText(knowledgeBase.getVectorCollection())
                ? knowledgeBase.getVectorCollection().trim()
                : properties.getQdrant().getCollectionName();
        EmbeddingStore<TextSegment> store = resolveStore(
                collectionName, binding.descriptor().dimension());
        return new VectorContext(
                binding.descriptor(), binding.model(),
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
        EmbeddingRuntimeBinding binding = resolveBinding(knowledgeBase);
        return new VectorContext(
                binding.descriptor(), binding.model(),
                resolveStore(collectionName.trim(),
                        binding.descriptor().dimension()),
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

    /** 创建或显式重建索引前刷新并持久化所需的模型快照字段。 */
    public EmbeddingRuntimeDescriptor prepare(AiKnowledgeBase knowledgeBase)
    {
        if (knowledgeBase == null) {
            throw new IllegalArgumentException("知识库不能为空");
        }
        if (knowledgeBase.getEmbeddingModelId() == null) {
            throw new ServiceException("TEXT_EMBEDDING_MODEL_REQUIRED");
        }
        EmbeddingRuntimeDescriptor descriptor = runtimeGateway.resolve(
                knowledgeBase.getEmbeddingModelId()).descriptor();
        knowledgeBase.setEmbeddingModelId(descriptor.modelId());
        knowledgeBase.setEmbeddingDimension(descriptor.dimension());
        knowledgeBase.setEmbeddingModelRevision(descriptor.modelRevision());
        knowledgeBase.setEmbeddingSchemaHash(descriptor.schemaHash());
        return descriptor;
    }

    public String newVersionCollection(AiKnowledgeBase knowledgeBase, long version)
    {
        if (knowledgeBase.getEmbeddingModelId() == null) {
            throw new ServiceException("TEXT_EMBEDDING_MODEL_REQUIRED");
        }
        String prefix = properties.getQdrant().getCollectionName().replaceAll("[^A-Za-z0-9_-]", "_");
        return prefix + "_kb" + knowledgeBase.getId()
                + "_m" + knowledgeBase.getEmbeddingModelId()
                + "_v" + version;
    }

    private EmbeddingRuntimeBinding resolveBinding(
            AiKnowledgeBase knowledgeBase)
    {
        if (knowledgeBase.getEmbeddingModelId() == null) {
            throw new ServiceException("TEXT_EMBEDDING_MODEL_REQUIRED");
        }
        EmbeddingRuntimeBinding binding = runtimeGateway.resolve(
                knowledgeBase.getEmbeddingModelId());
        EmbeddingRuntimeDescriptor descriptor = binding.descriptor();
        if (knowledgeBase.getEmbeddingDimension() == null
                || knowledgeBase.getEmbeddingModelRevision() == null
                || !hasText(knowledgeBase.getEmbeddingSchemaHash())) {
            throw new ServiceException(
                    "KNOWLEDGE_EMBEDDING_SNAPSHOT_REQUIRED");
        }
        if (knowledgeBase.getEmbeddingDimension() != descriptor.dimension()) {
            throw new ServiceException("EMBEDDING_DIMENSION_MISMATCH");
        }
        if (knowledgeBase.getEmbeddingModelRevision()
                != descriptor.modelRevision()
                || !knowledgeBase.getEmbeddingSchemaHash()
                .equals(descriptor.schemaHash())) {
            throw new ServiceException("KNOWLEDGE_EMBEDDING_SNAPSHOT_STALE");
        }
        return binding;
    }

    private EmbeddingStore<TextSegment> resolveStore(
            String collectionName,
            int dimension)
    {
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
        EmbeddingStore<TextSegment> store = QdrantEmbeddingStore.builder()
                .client(qdrantClient)
                .collectionName(collectionName)
                .payloadTextKey(properties.getQdrant().getPayloadTextKey())
                .build();
        return new ObservedEmbeddingStore<>(store, collectionName);
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
            EmbeddingRuntimeDescriptor descriptor,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            String collectionName)
    {
    }

    private record StoreBinding(EmbeddingStore<TextSegment> store, int dimension)
    {
    }
}
