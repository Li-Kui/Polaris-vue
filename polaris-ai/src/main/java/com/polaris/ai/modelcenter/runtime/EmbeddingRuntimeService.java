package com.polaris.ai.modelcenter.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.modelcenter.service.ModelAggregateAccessGuard;
import com.polaris.ai.runtime.CapabilityExecutor;
import com.polaris.ai.runtime.ModelExecutionResult;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.embedding.*;
import com.polaris.common.exception.ServiceException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** TEXT_EMBEDDING 的统一 Runtime 入口与维度探测服务。 */
@Service
public class EmbeddingRuntimeService implements EmbeddingRuntimeGateway {

    private static final String CAPABILITY = "TEXT_EMBEDDING";
    private static final int MAX_DESCRIPTOR_CACHE_ENTRIES = 1_024;
    private static final String DIMENSION_PROBE_TEXT = "test";

    private final ModelDefinitionResolver definitionResolver;
    private final ModelRuntimeResolver runtimeResolver;
    private final CapabilityExecutor capabilityExecutor;
    private final ModelAggregateAccessGuard accessGuard;
    private final ConcurrentMap<DescriptorKey, EmbeddingRuntimeDescriptor>
            descriptorCache = new ConcurrentHashMap<>();

    public EmbeddingRuntimeService(
            ModelDefinitionResolver definitionResolver,
            ModelRuntimeResolver runtimeResolver,
            CapabilityExecutor capabilityExecutor,
            ModelAggregateAccessGuard accessGuard) {
        this.definitionResolver = definitionResolver;
        this.runtimeResolver = runtimeResolver;
        this.capabilityExecutor = capabilityExecutor;
        this.accessGuard = accessGuard;
    }

    @Override
    public EmbeddingRuntimeBinding resolve(Long modelId) {
        ResolvedModelDefinition definition = definitionResolver.resolve(
                modelId, CAPABILITY);
        AiModelConfig model = accessGuard.requireAccessible(modelId);
        DescriptorKey key = new DescriptorKey(
                definition.modelId(), definition.modelRevision(),
                definition.connectionRevision(),
                definition.invocation().schemaReference().schemaHash(),
                definition.runtimeDefinitionHash());
        if (descriptorCache.size() >= MAX_DESCRIPTOR_CACHE_ENTRIES
                && !descriptorCache.containsKey(key)) {
            descriptorCache.clear();
        }
        EmbeddingRuntimeDescriptor descriptor = descriptorCache.computeIfAbsent(
                key, ignored -> describe(definition, model));
        return new EmbeddingRuntimeBinding(
                descriptor, new RuntimeEmbeddingModel(this, descriptor));
    }

    @Override
    public ModelExecutionResult<EmbeddingResponse> execute(
            Long modelId,
            EmbeddingRequest request) {
        EmbeddingCapabilityInvocation invocation =
                new EmbeddingCapabilityInvocation(request);
        ModelRuntimeSpec runtime = runtimeResolver.resolve(
                new ModelRuntimeRequest<>(
                        modelId, invocation, null, null));
        return capabilityExecutor.execute(
                runtime, invocation, EmbeddingResponse.class);
    }

    private EmbeddingRuntimeDescriptor describe(
            ResolvedModelDefinition definition,
            AiModelConfig model) {
        ObjectNode parameters = definition.invocation().parameters();
        Integer configuredDimension = optionalPositiveInteger(
                parameters.get("dimension"), "dimension");
        int batchSize = requiredPositiveInteger(
                parameters.get("batchSize"), "batchSize");
        int actualDimension = probeDimension(definition.modelId());
        if (configuredDimension != null
                && configuredDimension != actualDimension) {
            throw new ServiceException("EMBEDDING_DIMENSION_MISMATCH: configured="
                    + configuredDimension + ", actual=" + actualDimension);
        }
        SchemaReference schema = definition.invocation().schemaReference();
        return new EmbeddingRuntimeDescriptor(
                definition.modelId(), definition.modelName(),
                definition.modelRevision(), definition.connectionRevision(),
                actualDimension, batchSize, schema.schemaVersion(),
                schema.schemaHash(), definition.runtimeDefinitionHash(),
                model.getTenantId(), model.getDeptId());
    }

    private int probeDimension(Long modelId) {
        EmbeddingResponse response = execute(modelId,
                EmbeddingRequest.builder().input(DIMENSION_PROBE_TEXT).build())
                .value();
        List<Embedding> embeddings = response == null
                ? null : response.embeddings();
        if (embeddings == null || embeddings.size() != 1
                || embeddings.get(0) == null
                || embeddings.get(0).dimension() <= 0) {
            throw new ServiceException("EMBEDDING_DIMENSION_PROBE_FAILED");
        }
        return embeddings.get(0).dimension();
    }

    private Integer optionalPositiveInteger(JsonNode value, String field) {
        if (value == null || value.isNull()) {
            return null;
        }
        return requiredPositiveInteger(value, field);
    }

    private int requiredPositiveInteger(JsonNode value, String field) {
        if (value == null || !value.isIntegralNumber()
                || !value.canConvertToInt() || value.intValue() <= 0) {
            throw new ServiceException(
                    "MODEL_CONFIG_INVALID: " + field);
        }
        return value.intValue();
    }

    private record DescriptorKey(
            Long modelId,
            long modelRevision,
            long connectionRevision,
            String schemaHash,
            String runtimeDefinitionHash) {

        private DescriptorKey {
            Objects.requireNonNull(modelId, "modelId");
            Objects.requireNonNull(schemaHash, "schemaHash");
        }
    }
}
