package com.polaris.ai.runtime.embedding;

import com.polaris.ai.pivot.AiModelFactory;
import com.polaris.ai.runtime.CapabilityAdapter;
import com.polaris.ai.runtime.ModelExecutionResult;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import com.polaris.ai.runtime.usage.UsageSource;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.Map;

/** OpenAI-compatible TEXT_EMBEDDING Runtime Adapter。 */
@Component
public class EmbeddingCapabilityAdapter implements
        CapabilityAdapter<EmbeddingCapabilityInvocation, EmbeddingResponse> {

    private final AiModelFactory modelFactory;

    public EmbeddingCapabilityAdapter(AiModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    @Override
    public String capabilityCode() {
        return EmbeddingCapabilityInvocation.CAPABILITY_CODE;
    }

    @Override
    public Class<EmbeddingCapabilityInvocation> invocationType() {
        return EmbeddingCapabilityInvocation.class;
    }

    @Override
    public Class<EmbeddingResponse> resultType() {
        return EmbeddingResponse.class;
    }

    @Override
    public ModelExecutionResult<EmbeddingResponse> execute(
            ModelRuntimeSpec runtime,
            EmbeddingCapabilityInvocation invocation) {
        EmbeddingResponse response = modelFactory.createEmbeddingModel(runtime)
                .embed(invocation.request());
        return new ModelExecutionResult<>(response, normalize(response), null);
    }

    private NormalizedUsage normalize(EmbeddingResponse response) {
        TokenUsage usage = response == null ? null : response.tokenUsage();
        if (usage == null) {
            return NormalizedUsage.unknown();
        }
        return new NormalizedUsage(
                usage.inputTokenCount(), usage.outputTokenCount(),
                usage.totalTokenCount(), null, null, null,
                Map.of(), UsageSource.PROVIDER_REPORTED);
    }
}
