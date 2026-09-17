package com.polaris.ai.observability;

import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** 采集 Embedding 批次大小、耗时和失败结果。 */
public final class ObservedEmbeddingModel implements EmbeddingModel, AutoCloseable
{
    private final EmbeddingModel delegate;
    private final String provider;
    private final String model;

    public ObservedEmbeddingModel(EmbeddingModel delegate, String provider, String model)
    {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.provider = provider;
        this.model = model;
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request)
    {
        long startedAt = AiObservability.start();
        try {
            EmbeddingResponse response = delegate.embed(request);
            AiObservability.recordEmbedding(provider, model, batchSize(request), startedAt, null);
            return response;
        } catch (RuntimeException error) {
            AiObservability.recordEmbedding(provider, model, batchSize(request), startedAt, error);
            throw error;
        }
    }

    @Override
    public CompletableFuture<EmbeddingResponse> embedAsync(EmbeddingRequest request)
    {
        long startedAt = AiObservability.start();
        return delegate.embedAsync(request).whenComplete((response, error) ->
                AiObservability.recordEmbedding(
                        provider, model, batchSize(request), startedAt, error));
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request)
    {
        return embed(request);
    }

    @Override
    public CompletableFuture<EmbeddingResponse> doEmbedAsync(EmbeddingRequest request)
    {
        return embedAsync(request);
    }

    private int batchSize(EmbeddingRequest request)
    {
        return request == null || request.inputs() == null ? 0 : request.inputs().size();
    }

    @Override
    public List<EmbeddingModelListener> listeners()
    {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider()
    {
        return delegate.provider();
    }

    @Override
    public EmbeddingRequestParameters defaultRequestParameters()
    {
        return delegate.defaultRequestParameters();
    }

    @Override
    public Set<EmbeddingParameter<?>> supportedParameters()
    {
        return delegate.supportedParameters();
    }

    @Override
    public Set<ContentType> supportedContentTypes()
    {
        return delegate.supportedContentTypes();
    }

    @Override
    public int dimension()
    {
        return delegate.dimension();
    }

    @Override
    public String modelName()
    {
        return delegate.modelName();
    }

    @Override
    public void close() throws Exception
    {
        if (delegate instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
