package com.polaris.ai.runtime.embedding;

import java.util.Objects;

/** 知识库持久化和运行校验所需的 Embedding 非 Secret 元数据。 */
public record EmbeddingRuntimeDescriptor(
        Long modelId,
        String modelName,
        long modelRevision,
        long connectionRevision,
        int dimension,
        int batchSize,
        int schemaVersion,
        String schemaHash,
        String runtimeDefinitionHash,
        Long tenantId,
        Long deptId) {

    public EmbeddingRuntimeDescriptor {
        modelId = Objects.requireNonNull(modelId, "modelId");
        if (dimension <= 0) {
            throw new IllegalArgumentException("Embedding dimension 必须大于 0");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Embedding batchSize 必须大于 0");
        }
        schemaHash = Objects.requireNonNull(schemaHash, "schemaHash");
    }
}
