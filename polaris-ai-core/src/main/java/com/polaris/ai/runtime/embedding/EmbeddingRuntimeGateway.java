package com.polaris.ai.runtime.embedding;

import com.polaris.ai.runtime.ModelExecutionResult;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;

/** Core RAG 与 Model Center Runtime 之间的依赖倒置边界。 */
public interface EmbeddingRuntimeGateway {

    EmbeddingRuntimeBinding resolve(Long modelId);

    ModelExecutionResult<EmbeddingResponse> execute(
            Long modelId,
            EmbeddingRequest request);
}
