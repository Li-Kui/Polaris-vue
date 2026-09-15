package com.polaris.ai.observability;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** 为 Qdrant EmbeddingStore 采集查询耗时，同时完整保留存储接口行为。 */
public final class ObservedEmbeddingStore<Embedded> implements EmbeddingStore<Embedded>
{
    private final EmbeddingStore<Embedded> delegate;
    private final String collection;

    public ObservedEmbeddingStore(EmbeddingStore<Embedded> delegate, String collection)
    {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.collection = collection;
    }

    @Override
    public String add(Embedding embedding)
    {
        return delegate.add(embedding);
    }

    @Override
    public void add(String id, Embedding embedding)
    {
        delegate.add(id, embedding);
    }

    @Override
    public String add(Embedding embedding, Embedded embedded)
    {
        return delegate.add(embedding, embedded);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings)
    {
        return delegate.addAll(embeddings);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<Embedded> embedded)
    {
        return delegate.addAll(embeddings, embedded);
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<Embedded> embedded)
    {
        delegate.addAll(ids, embeddings, embedded);
    }

    @Override
    public void remove(String id)
    {
        delegate.remove(id);
    }

    @Override
    public void removeAll(Collection<String> ids)
    {
        delegate.removeAll(ids);
    }

    @Override
    public void removeAll(Filter filter)
    {
        delegate.removeAll(filter);
    }

    @Override
    public void removeAll()
    {
        delegate.removeAll();
    }

    @Override
    public EmbeddingSearchResult<Embedded> search(EmbeddingSearchRequest request)
    {
        long startedAt = AiObservability.start();
        try {
            EmbeddingSearchResult<Embedded> result = delegate.search(request);
            AiObservability.recordQdrant("search", collection, startedAt, null);
            return result;
        } catch (RuntimeException error) {
            AiObservability.recordQdrant("search", collection, startedAt, error);
            throw error;
        }
    }

    @Override
    public CompletableFuture<EmbeddingSearchResult<Embedded>> searchAsync(
            EmbeddingSearchRequest request)
    {
        long startedAt = AiObservability.start();
        return delegate.searchAsync(request).whenComplete((result, error) ->
                AiObservability.recordQdrant("search", collection, startedAt, error));
    }
}
