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
    private final StorageIdentity storageIdentity;
    private final Map<String, StoreBinding> stores = new ConcurrentHashMap<>();
    private final Map<String, String> localOwnership = new ConcurrentHashMap<>();

    public AiVectorStoreResolver(
            AiVectorStoreProperties properties,
            EmbeddingRuntimeGateway runtimeGateway,
            EmbeddingStore<TextSegment> defaultStore,
            ObjectProvider<QdrantClient> qdrantClientProvider)
    {
        this.properties = properties;
        this.runtimeGateway = runtimeGateway;
        this.qdrantClient = qdrantClientProvider.getIfAvailable();
        // 冻结客户端初始化时的路由；不保存 API key，不将主机摘要冒充不可变集群身份。
        String route = properties.getType() == AiVectorStoreProperties.Type.MEMORY
                ? "MEMORY|" + java.util.UUID.randomUUID()
                : "QDRANT|" + properties.getQdrant().getHost().trim().toLowerCase(java.util.Locale.ROOT)
                    + "|" + properties.getQdrant().getPort() + "|" + properties.getQdrant().isUseTls();
        this.storageIdentity = new StorageIdentity(properties.getType().name(), routeDigest(route));
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

    /** 仅用于已登记的新重建候选，不能复用本机缓存或接管远端同名集合。 */
    public VectorContext resolveNewCollection(AiKnowledgeBase knowledgeBase, String collectionName, String ownershipMarker)
    {
        if (knowledgeBase == null || collectionName == null || !collectionName.matches("[A-Za-z0-9_-]{1,255}")) {
            throw new IllegalArgumentException("新候选标识无效");
        }
        if (ownershipMarker == null || !ownershipMarker.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("新候选归属标记无效");
        requireStorageRoute();
        if (stores.containsKey(collectionName)) throw new IllegalStateException("新候选不能复用已有 collection");
        EmbeddingRuntimeBinding binding = resolveBinding(knowledgeBase);
        EmbeddingStore<TextSegment> store;
        if (properties.getType() == AiVectorStoreProperties.Type.MEMORY) {
            store = new InMemoryEmbeddingStore<>();
        } else {
            if (qdrantClient == null) throw new IllegalStateException("Qdrant 客户端未初始化");
            var copy = copyQdrantProperties();
            copy.setCollectionName(collectionName); copy.setDimension(binding.descriptor().dimension());
            QdrantCollectionInitializer.initializeNew(qdrantClient, copy, ownershipMarker);
            store = buildQdrantStore(collectionName);
        }
        if (stores.putIfAbsent(collectionName, new StoreBinding(store, binding.descriptor().dimension())) != null)
            throw new IllegalStateException("新候选本机绑定已变化");
        if (properties.getType() == AiVectorStoreProperties.Type.MEMORY) localOwnership.put(collectionName,ownershipMarker);
        return new VectorContext(binding.descriptor(), binding.model(), store, collectionName);
    }

    /** 只读取一个已登记集合的配置，不解析模型、不创建集合、不补写标记、不读取文档正文。 */
    public OwnershipCheck verifyCollectionOwner(String collectionName,String ownershipMarker) {
        if (collectionName == null || !collectionName.matches("[A-Za-z0-9_-]{1,255}")
                || ownershipMarker == null || !ownershipMarker.matches("[a-f0-9]{64}")) return OwnershipCheck.UNVERIFIED;
        try {
            requireStorageRoute();
            if (properties.getType() == AiVectorStoreProperties.Type.MEMORY)
                return ownershipMarker.equals(localOwnership.get(collectionName)) && stores.containsKey(collectionName)
                        ? OwnershipCheck.LOCAL_MATCHED : OwnershipCheck.LOCAL_MISSING;
            if (qdrantClient == null) return OwnershipCheck.UNAVAILABLE;
            var info = qdrantClient.getCollectionInfoAsync(collectionName,properties.getQdrant().getTimeout())
                    .get(properties.getQdrant().getTimeout().toMillis(),java.util.concurrent.TimeUnit.MILLISECONDS);
            return QdrantCollectionInitializer.ownerMatches(info,ownershipMarker);
        } catch (InterruptedException e) {Thread.currentThread().interrupt();return OwnershipCheck.UNAVAILABLE;}
        catch (RuntimeException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            // 404/权限/网络/不支持等不确定结果均不能解释为安全可删，不复制远端异常正文。
            return OwnershipCheck.UNAVAILABLE;
        }
    }

    public enum OwnershipCheck {MATCHED, LOCAL_MATCHED, LOCAL_MISSING, MARKER_MISSING, MARKER_MISMATCH, UNVERIFIED, UNAVAILABLE}

    /** 仅由已持久领取的清理任务调用；必须处于可信、无外部改名/恢复/替换的受控存储窗口。 */
    public CollectionDeletion deleteOwnedCollection(String name,String marker,java.util.function.BooleanSupplier dispatchGuard) {
        if (name==null || !name.matches("[A-Za-z0-9_-]{1,255}") || marker==null || !marker.matches("[a-f0-9]{64}") || dispatchGuard==null)
            return CollectionDeletion.OWNERSHIP_REJECTED;
        try {requireStorageRoute();} catch(RuntimeException e) {return CollectionDeletion.OWNERSHIP_REJECTED;}
        if (properties.getType()!=AiVectorStoreProperties.Type.QDRANT) return CollectionDeletion.OWNERSHIP_REJECTED;
        if (qdrantClient==null) return CollectionDeletion.PRECHECK_UNAVAILABLE;
        var configured=properties.getQdrant().getTimeout();
        var timeout=configured==null || configured.isNegative() || configured.isZero() || configured.compareTo(java.time.Duration.ofSeconds(10))>0
                ?java.time.Duration.ofSeconds(10):configured;
        try {
            var info=qdrantClient.getCollectionInfoAsync(name,timeout).get(Math.max(1,timeout.toMillis()),java.util.concurrent.TimeUnit.MILLISECONDS);
            if (QdrantCollectionInitializer.ownerMatches(info,marker)!=OwnershipCheck.MATCHED) return CollectionDeletion.OWNERSHIP_REJECTED;
        } catch(InterruptedException e) {Thread.currentThread().interrupt();return CollectionDeletion.PRECHECK_UNAVAILABLE;}
        catch(RuntimeException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {return CollectionDeletion.PRECHECK_UNAVAILABLE;}
        // guard 必须独立提交派发意图；提交异常也不能当作“确定没有派发，可以重试”。
        try {if (!dispatchGuard.getAsBoolean()) return CollectionDeletion.PRECHECK_CHANGED;}
        catch(RuntimeException e) {return CollectionDeletion.DELETE_UNCONFIRMED;}
        try {
            requireStorageRoute();
            var result=qdrantClient.deleteCollectionAsync(name,timeout).get(Math.max(1,timeout.toMillis()),java.util.concurrent.TimeUnit.MILLISECONDS);
            if (result==null || !result.getResult()) return CollectionDeletion.DELETE_UNCONFIRMED;
            Boolean exists=qdrantClient.collectionExistsAsync(name,timeout).get(Math.max(1,timeout.toMillis()),java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!Boolean.FALSE.equals(exists)) return CollectionDeletion.DELETE_UNCONFIRMED;
            stores.remove(name);localOwnership.remove(name);
            return CollectionDeletion.DELETED;
        } catch(InterruptedException e) {Thread.currentThread().interrupt();return CollectionDeletion.DELETE_UNCONFIRMED;}
        catch(RuntimeException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {return CollectionDeletion.DELETE_UNCONFIRMED;}
    }
    public enum CollectionDeletion {DELETED, PRECHECK_UNAVAILABLE, OWNERSHIP_REJECTED, PRECHECK_CHANGED, DELETE_UNCONFIRMED}

    private void requireStorageRoute() {
        String configuredRoute = properties.getType() == AiVectorStoreProperties.Type.QDRANT
                ? "QDRANT|" + properties.getQdrant().getHost().trim().toLowerCase(java.util.Locale.ROOT)
                    + "|" + properties.getQdrant().getPort() + "|" + properties.getQdrant().isUseTls() : null;
        if (!storageIdentity.type().equals(properties.getType().name())
                || configuredRoute != null && !storageIdentity.key().equals(routeDigest(configuredRoute)))
            throw new IllegalStateException("向量存储路由已变化，请重启后再提交新构建");
    }

    public StorageIdentity storageIdentity() {return storageIdentity;}

    private static String routeDigest(String value)
    {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }

    public record StorageIdentity(String type, String key) { }

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
