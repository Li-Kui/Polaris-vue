package com.polaris.ai.modelcenter.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.modelcenter.service.IAiProviderConnectionService;
import com.polaris.ai.modelcenter.vo.ProviderConnectionRuntime;
import com.polaris.ai.runtime.CapabilityInvocation;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.RuntimePolicySpec;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Definition + Active Feature + Override + Current Credential 的请求级解析器。 */
@Service
public class DefaultModelRuntimeResolver implements ModelRuntimeResolver {

    private static final int MAX_CONSISTENCY_ATTEMPTS = 2;

    private final ModelDefinitionResolver definitionResolver;
    private final FeatureActivationResolver featureActivationResolver;
    private final CapabilityParameterComposer parameterComposer;
    private final RuntimePolicyResolver runtimePolicyResolver;
    private final IAiProviderConnectionService connectionService;
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    public DefaultModelRuntimeResolver(
            ModelDefinitionResolver definitionResolver,
            FeatureActivationResolver featureActivationResolver,
            CapabilityParameterComposer parameterComposer,
            RuntimePolicyResolver runtimePolicyResolver,
            IAiProviderConnectionService connectionService) {
        this.definitionResolver = definitionResolver;
        this.featureActivationResolver = featureActivationResolver;
        this.parameterComposer = parameterComposer;
        this.runtimePolicyResolver = runtimePolicyResolver;
        this.connectionService = connectionService;
    }

    @Override
    public <I extends CapabilityInvocation> ModelRuntimeSpec resolve(
            ModelRuntimeRequest<I> request) {
        if (request == null) {
            throw new ServiceException("MODEL_RUNTIME_REQUEST_REQUIRED");
        }
        for (int attempt = 0; attempt < MAX_CONSISTENCY_ATTEMPTS; attempt++) {
            ResolvedModelDefinition definition = definitionResolver.resolve(
                    request.modelId(), request.invocation().capabilityCode());
            Set<String> activeFeatures = featureActivationResolver.resolve(
                    definition, request.requestedFeatures());
            ComposedCapabilityParameters parameters = parameterComposer.compose(
                    definition, activeFeatures, request.overrides());
            RuntimePolicySpec policy = runtimePolicyResolver.resolve(
                    definition.runtimePolicyLayers());
            ProviderConnectionRuntime connection =
                    connectionService.getRuntime(definition.connectionId());
            if (!sameConnection(definition, connection)) {
                continue;
            }
            return toRuntimeSpec(
                    definition, parameters, activeFeatures, policy, connection);
        }
        throw new ServiceException("MODEL_RUNTIME_CHANGED_RETRY");
    }

    private ModelRuntimeSpec toRuntimeSpec(
            ResolvedModelDefinition definition,
            ComposedCapabilityParameters parameters,
            Set<String> activeFeatures,
            RuntimePolicySpec policy,
            ProviderConnectionRuntime connection) {
        SchemaReference schema = definition.invocation().schemaReference();
        return new ModelRuntimeSpec(
                definition.modelId(), definition.modelCode(),
                definition.modelRevision(), definition.connectionId(),
                definition.connectionRevision(), definition.providerCode(),
                definition.protocolCode(), definition.networkMode(),
                definition.baseUrl(), definition.modelName(),
                schema.capabilityCode(), map(parameters.invocation()),
                featureMaps(parameters.features()), activeFeatures,
                map(definition.providerExtraConfig()),
                connection.credentials(), policy, schema.schemaVersion(),
                schema.schemaHash(), definition.runtimeDefinitionHash());
    }

    private boolean sameConnection(
            ResolvedModelDefinition definition,
            ProviderConnectionRuntime current) {
        return current != null
                && Objects.equals(definition.connectionId(), current.id())
                && definition.connectionRevision() == current.revision()
                && Objects.equals(definition.providerCode(),
                current.providerCode())
                && Objects.equals(definition.protocolCode(),
                current.protocolCode())
                && Objects.equals(definition.networkMode(),
                current.networkMode())
                && Objects.equals(definition.baseUrl(), current.baseUrl());
    }

    private Map<String, Object> map(ObjectNode node) {
        return objectMapper.convertValue(node,
                new TypeReference<Map<String, Object>>() { });
    }

    private Map<String, Map<String, Object>> featureMaps(
            Map<String, ObjectNode> source) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        source.forEach((code, value) -> result.put(code, map(value)));
        return Map.copyOf(result);
    }
}
