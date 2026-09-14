package com.polaris.ai.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 每次调用时重新解析当前生效的向量模型。
 *
 * <p>完整转发 LangChain4j 1.20 的请求式、异步、兼容旧版和元数据接口。
 * 监听器装饰能力继续使用接口提供的 {@code addListener(s)} 默认实现。</p>
 */
public final class HotSwappableEmbeddingModel implements EmbeddingModel
{

    private final Supplier<EmbeddingModel> delegateSupplier;

    public HotSwappableEmbeddingModel(Supplier<EmbeddingModel> delegateSupplier)
    {
        this.delegateSupplier = Objects.requireNonNull(delegateSupplier, "delegateSupplier");
    }

    private EmbeddingModel delegate()
    {
        return Objects.requireNonNull(delegateSupplier.get(), "active embedding model");
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request)
    {
        return delegate().embed(request);
    }

    @Override
    public CompletableFuture<EmbeddingResponse> embedAsync(EmbeddingRequest request)
    {
        return delegate().embedAsync(request);
    }

    @Override
    public List<EmbeddingModelListener> listeners()
    {
        return delegate().listeners();
    }

    @Override
    public ModelProvider provider()
    {
        return delegate().provider();
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request)
    {
        return delegate().doEmbed(request);
    }

    @Override
    public CompletableFuture<EmbeddingResponse> doEmbedAsync(EmbeddingRequest request)
    {
        return delegate().doEmbedAsync(request);
    }

    @Override
    public EmbeddingRequestParameters defaultRequestParameters()
    {
        return delegate().defaultRequestParameters();
    }

    @Override
    public Set<EmbeddingParameter<?>> supportedParameters()
    {
        return delegate().supportedParameters();
    }

    @Override
    public Set<ContentType> supportedContentTypes()
    {
        return delegate().supportedContentTypes();
    }

    @Override
    public Response<Embedding> embed(String text)
    {
        return delegate().embed(text);
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment)
    {
        return delegate().embed(textSegment);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments)
    {
        return delegate().embedAll(textSegments);
    }

    @Override
    public int dimension()
    {
        return delegate().dimension();
    }

    @Override
    public String modelName()
    {
        return delegate().modelName();
    }
}
