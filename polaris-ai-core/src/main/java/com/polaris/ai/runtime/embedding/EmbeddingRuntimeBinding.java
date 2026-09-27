package com.polaris.ai.runtime.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.Objects;

/** 单次知识库操作使用的 Embedding Model 与已验证描述。 */
public record EmbeddingRuntimeBinding(
        EmbeddingRuntimeDescriptor descriptor,
        EmbeddingModel model) {

    public EmbeddingRuntimeBinding {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        model = Objects.requireNonNull(model, "model");
    }
}
