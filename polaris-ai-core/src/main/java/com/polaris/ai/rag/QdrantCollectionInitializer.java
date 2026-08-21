package com.polaris.ai.rag;

import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

/**
 * 创建并校验 Polaris 使用的 Qdrant collection。
 */
final class QdrantCollectionInitializer
{
    private static final String KNOWLEDGE_BASE_ID = "knowledge_base_id";
    private static final String DOCUMENT_ID = "document_id";

    private QdrantCollectionInitializer()
    {
    }

    static void initialize(QdrantClient client, AiVectorStoreProperties.QdrantProperties properties)
    {
        validate(properties);

        String collectionName = properties.getCollectionName();
        Duration timeout = properties.getTimeout();
        boolean exists = await(client.collectionExistsAsync(collectionName, timeout), "检查 Qdrant collection");

        if (!exists) {
            if (!properties.isInitializeSchema()) {
                throw new IllegalStateException("Qdrant collection 不存在且 initialize-schema=false: " + collectionName);
            }
            createCollection(client, properties);
        }

        Collections.CollectionInfo info = await(
                client.getCollectionInfoAsync(collectionName, timeout),
                "读取 Qdrant collection 配置");
        validateCollection(info, properties);

        if (properties.isCreatePayloadIndexes()) {
            createPayloadIndexIfMissing(client, info, collectionName, KNOWLEDGE_BASE_ID, timeout);
            createPayloadIndexIfMissing(client, info, collectionName, DOCUMENT_ID, timeout);
        }
    }

    private static void createCollection(QdrantClient client, AiVectorStoreProperties.QdrantProperties properties)
    {
        Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                .setSize(properties.getDimension())
                .setDistance(Collections.Distance.Cosine)
                .setOnDisk(properties.isOnDisk())
                .build();

        try {
            await(client.createCollectionAsync(
                    properties.getCollectionName(), vectorParams, properties.getTimeout()),
                    "创建 Qdrant collection");
        } catch (IllegalStateException creationFailure) {
            boolean createdByAnotherInstance = await(client.collectionExistsAsync(
                    properties.getCollectionName(), properties.getTimeout()),
                    "复核 Qdrant collection");
            if (!createdByAnotherInstance) {
                throw creationFailure;
            }
        }
    }

    private static void validateCollection(
            Collections.CollectionInfo info,
            AiVectorStoreProperties.QdrantProperties properties)
    {
        if (!info.hasConfig()
                || !info.getConfig().hasParams()
                || !info.getConfig().getParams().hasVectorsConfig()
                || !info.getConfig().getParams().getVectorsConfig().hasParams()) {
            throw new IllegalStateException("Qdrant collection 必须使用单一默认向量配置: "
                    + properties.getCollectionName());
        }

        Collections.VectorParams actual = info.getConfig().getParams().getVectorsConfig().getParams();
        if (actual.getSize() != properties.getDimension()) {
            throw new IllegalStateException(String.format(
                    "Qdrant collection 向量维度不匹配: collection=%s, expected=%d, actual=%d",
                    properties.getCollectionName(), properties.getDimension(), actual.getSize()));
        }
        if (actual.getDistance() != Collections.Distance.Cosine) {
            throw new IllegalStateException("Qdrant collection 距离算法必须为 Cosine: "
                    + properties.getCollectionName());
        }
    }

    private static void createPayloadIndexIfMissing(
            QdrantClient client,
            Collections.CollectionInfo info,
            String collectionName,
            String fieldName,
            Duration timeout)
    {
        if (info.containsPayloadSchema(fieldName)) {
            return;
        }

        Points.CreateFieldIndexCollection request = Points.CreateFieldIndexCollection.newBuilder()
                .setCollectionName(collectionName)
                .setFieldName(fieldName)
                .setFieldType(Points.FieldType.FieldTypeKeyword)
                .setWait(true)
                .build();
        try {
            await(client.createPayloadIndexAsync(request, timeout), "创建 Qdrant payload 索引 " + fieldName);
        } catch (IllegalStateException raceFailure) {
            Collections.CollectionInfo refreshed = await(
                    client.getCollectionInfoAsync(collectionName, timeout),
                    "复核 Qdrant payload 索引 " + fieldName);
            if (!refreshed.containsPayloadSchema(fieldName)) {
                throw raceFailure;
            }
        }
    }

    private static void validate(AiVectorStoreProperties.QdrantProperties properties)
    {
        if (properties.getHost() == null || properties.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException("ai.vector-store.qdrant.host 不能为空");
        }
        if (properties.getPort() <= 0 || properties.getPort() > 65535) {
            throw new IllegalArgumentException("ai.vector-store.qdrant.port 必须处于 1-65535");
        }
        if (properties.getCollectionName() == null || properties.getCollectionName().trim().isEmpty()) {
            throw new IllegalArgumentException("ai.vector-store.qdrant.collection-name 不能为空");
        }
        if (properties.getDimension() <= 0) {
            throw new IllegalArgumentException("ai.vector-store.qdrant.dimension 必须大于 0");
        }
        if (properties.getPayloadTextKey() == null || properties.getPayloadTextKey().trim().isEmpty()) {
            throw new IllegalArgumentException("ai.vector-store.qdrant.payload-text-key 不能为空");
        }
        if (properties.getTimeout() == null || properties.getTimeout().isNegative() || properties.getTimeout().isZero()) {
            throw new IllegalArgumentException("ai.vector-store.qdrant.timeout 必须大于 0");
        }
    }

    private static <T> T await(ListenableFuture<T> future, String operation)
    {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(operation + " 被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException(operation + " 失败: " + cause.getMessage(), cause);
        }
    }
}
