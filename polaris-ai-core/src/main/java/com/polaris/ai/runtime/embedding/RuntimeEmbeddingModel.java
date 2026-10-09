package com.polaris.ai.runtime.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;

import java.util.Objects;

/** 每次调用都重新进入统一 Runtime 的 LangChain4j EmbeddingModel。 */
public final class RuntimeEmbeddingModel implements EmbeddingModel {

    private final EmbeddingRuntimeGateway gateway;
    private final EmbeddingRuntimeDescriptor descriptor;

    public RuntimeEmbeddingModel(
            EmbeddingRuntimeGateway gateway,
            EmbeddingRuntimeDescriptor descriptor) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        return gateway.execute(descriptor.modelId(), request).value();
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        return embed(request);
    }

    @Override
    public int dimension() {
        return descriptor.dimension();
    }

    @Override
    public String modelName() {
        return descriptor.modelName();
    }

    @Override
    public String toString() {
        return "RuntimeEmbeddingModel[modelId=" + descriptor.modelId()
                + ", dimension=" + descriptor.dimension() + "]";
    }
}
