package com.polaris.ai.modelcenter.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.domain.AiModelCapability;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.domain.AiModelRuntimePolicy;
import com.polaris.ai.modelcenter.protocol.ProtocolAdapter;
import com.polaris.ai.modelcenter.protocol.ProtocolAdapterRegistry;
import com.polaris.ai.modelcenter.schema.*;
import com.polaris.ai.modelcenter.service.IAiProviderConnectionService;
import com.polaris.ai.modelcenter.service.ModelAggregateAccessGuard;
import com.polaris.ai.modelcenter.service.ModelCapabilityInternalService;
import com.polaris.ai.modelcenter.service.ModelRuntimePolicyInternalService;
import com.polaris.ai.modelcenter.vo.ProviderConnectionVO;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/** 权限感知地解析并短期缓存不可变 Model Definition。 */
@Service
public class DefaultModelDefinitionResolver implements ModelDefinitionResolver {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int MAX_CACHE_ENTRIES = 1_024;
    private static final int MAX_JSON_BYTES = 65_536;
    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");

    private final ModelAggregateAccessGuard accessGuard;
    private final IAiProviderConnectionService connectionService;
    private final ModelCapabilityInternalService capabilityService;
    private final ModelRuntimePolicyInternalService runtimePolicyService;
    private final ProtocolAdapterRegistry protocolRegistry;
    private final ProviderProfileValueValidator providerProfiles;
    private final SchemaProfileRegistry profileRegistry;
    private final CapabilitySchemaRegistry capabilitySchemaRegistry;
    private final SchemaProfileResolver schemaResolver;
    private final CapabilityConfigProcessor configProcessor;
    private final ModelDefinitionHasher definitionHasher;
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();
    private final ConcurrentMap<DefinitionCacheKey, CacheEntry> cache =
            new ConcurrentHashMap<>();

    public DefaultModelDefinitionResolver(
            ModelAggregateAccessGuard accessGuard,
            IAiProviderConnectionService connectionService,
            ModelCapabilityInternalService capabilityService,
            ModelRuntimePolicyInternalService runtimePolicyService,
            ProtocolAdapterRegistry protocolRegistry,
            ProviderProfileValueValidator providerProfiles,
            SchemaProfileRegistry profileRegistry,
            CapabilitySchemaRegistry capabilitySchemaRegistry,
            SchemaProfileResolver schemaResolver,
            CapabilityConfigProcessor configProcessor,
            ModelDefinitionHasher definitionHasher) {
        this.accessGuard = accessGuard;
        this.connectionService = connectionService;
        this.capabilityService = capabilityService;
        this.runtimePolicyService = runtimePolicyService;
        this.protocolRegistry = protocolRegistry;
        this.providerProfiles = providerProfiles;
        this.profileRegistry = profileRegistry;
        this.capabilitySchemaRegistry = capabilitySchemaRegistry;
        this.schemaResolver = schemaResolver;
        this.configProcessor = configProcessor;
        this.definitionHasher = definitionHasher;
    }

    @Override
    public ResolvedModelDefinition resolve(
            Long modelId,
            String invocationCapabilityCode) {
        String capabilityCode = normalizeCode(invocationCapabilityCode);
        AiModelConfig model = accessGuard.requireAccessible(modelId);
        validateModel(model);
        ProviderConnectionVO connection = connectionService.get(
                model.getConnectionId());
        validateConnection(connection);
        ProtocolAdapter protocol = protocolRegistry.getRequired(
                connection.protocolCode());
        if (protocol.findEndpoint(capabilityCode).isEmpty()) {
            throw new ServiceException(
                    "CAPABILITY_NOT_SUPPORTED_BY_PROTOCOL: " + capabilityCode);
        }
        SchemaProfileDefinition providerProfile =
                providerProfiles.requireProvider(
                        connection.providerCode(), connection.protocolCode());
        if (!providerProfile.supportedCapabilities().isEmpty()
                && !providerProfile.supportedCapabilities()
                .contains(capabilityCode)) {
            throw new ServiceException(
                    "CAPABILITY_NOT_SUPPORTED_BY_PROVIDER: " + capabilityCode);
        }

        List<AiModelCapability> enabled =
                capabilityService.listEnabledByModelId(model.getId());
        AiModelCapability invocation = enabled.stream()
                .filter(item -> capabilityCode.equals(
                        normalizeStoredCode(item.getCapabilityCode())))
                .filter(item -> blank(item.getAppliesToCapabilityCode()))
                .findFirst()
                .orElseThrow(() -> new ServiceException(
                        "CAPABILITY_NOT_ENABLED: " + capabilityCode));
        List<AiModelCapability> featureRows = enabled.stream()
                .filter(item -> capabilityCode.equals(
                        normalizeStoredCode(item.getAppliesToCapabilityCode())))
                .toList();

        List<SchemaReference> references = new ArrayList<>();
        references.add(storedReference(invocation));
        featureRows.forEach(row -> references.add(storedReference(row)));
        DefinitionCacheKey key = new DefinitionCacheKey(
                model.getId(), model.getRevision(), connection.id(),
                connection.revision(), capabilityCode,
                definitionHasher.schemaFingerprint(references));
        long now = System.currentTimeMillis();
        prune(now);
        CacheEntry entry = cache.compute(key, (ignored, current) -> {
            if (current != null && current.expiresAtMillis() > now) {
                return current;
            }
            ResolvedModelDefinition definition = build(
                    model, connection, protocol, providerProfile,
                    invocation, featureRows, capabilityCode);
            return new CacheEntry(
                    definition, now + CACHE_TTL.toMillis());
        });
        return entry.definition();
    }

    private ResolvedModelDefinition build(
            AiModelConfig model,
            ProviderConnectionVO connection,
            ProtocolAdapter protocol,
            SchemaProfileDefinition providerProfile,
            AiModelCapability invocationRow,
            List<AiModelCapability> featureRows,
            String capabilityCode) {
        ResolvedCapabilityDefinition invocation = resolveCapability(
                invocationRow, model, connection,
                CapabilitySchemaDefinition.CapabilityKind.INVOCATION,
                capabilityCode);
        Map<String, ResolvedCapabilityDefinition> features =
                new LinkedHashMap<>();
        featureRows.stream()
                .sorted(java.util.Comparator.comparing(
                        AiModelCapability::getCapabilityCode))
                .forEach(row -> {
                    ResolvedCapabilityDefinition feature = resolveCapability(
                            row, model, connection,
                            CapabilitySchemaDefinition.CapabilityKind.FEATURE,
                            capabilityCode);
                    String code = feature.schemaReference().capabilityCode();
                    if (features.putIfAbsent(code, feature) != null) {
                        throw new ServiceException(
                                "MODEL_CONFIG_INVALID: 重复 Feature " + code);
                    }
                });
        RuntimePolicyLayers policies = runtimePolicies(
                model.getId(), capabilityCode);
        RuntimeProfileVersions profileVersions = profileVersions(
                connection, model.getModelName(), providerProfile);
        ObjectNode extraConfig = objectMapper.valueToTree(
                connection.extraConfig() == null
                        ? Map.of() : connection.extraConfig());
        ResolvedModelDefinition definition = new ResolvedModelDefinition(
                model.getId(), model.getModelCode(), model.getRevision(),
                connection.id(), connection.revision(),
                connection.providerCode(), connection.protocolCode(),
                protocol.adapterVersion(), profileVersions,
                connection.networkMode(), connection.baseUrl(), extraConfig,
                model.getModelName(), invocation, features, policies, null);
        return definition.withRuntimeDefinitionHash(
                definitionHasher.hash(definition));
    }

    private ResolvedCapabilityDefinition resolveCapability(
            AiModelCapability row,
            AiModelConfig model,
            ProviderConnectionVO connection,
            CapabilitySchemaDefinition.CapabilityKind expectedKind,
            String invocationCapability) {
        SchemaReference stored = storedReference(row);
        if (capabilitySchemaRegistry.find(
                stored.capabilityCode(), stored.schemaVersion()).isEmpty()) {
            throw new ServiceException(
                    "SCHEMA_VERSION_MISMATCH: " + stored.capabilityCode());
        }
        ResolvedCapabilitySchema resolved = schemaResolver.resolve(
                stored.capabilityCode(), stored.schemaVersion(),
                connection.protocolCode(), connection.providerCode(),
                model.getModelName());
        CapabilitySchemaDefinition schema = resolved.definition();
        if (!stored.schemaHash().equals(resolved.runtimeSchemaHash())) {
            throw new ServiceException(
                    "SCHEMA_HASH_MISMATCH: " + stored.capabilityCode());
        }
        if (schema.kind() != expectedKind) {
            throw new ServiceException(
                    "MODEL_CONFIG_INVALID: Capability Kind 不匹配");
        }
        String appliesTo = normalizeStoredCode(
                row.getAppliesToCapabilityCode());
        if (expectedKind == CapabilitySchemaDefinition.CapabilityKind.FEATURE
                && (!invocationCapability.equals(appliesTo)
                || !schema.allowedAppliesTo().contains(invocationCapability))) {
            throw new ServiceException(
                    "MODEL_CONFIG_INVALID: Feature appliesTo 不匹配");
        }
        ObjectNode parameters = configProcessor.process(
                schema, parseObject(row.getConfigJson(), "Capability config"));
        return new ResolvedCapabilityDefinition(
                new SchemaReference(schema.code(), schema.schemaVersion(),
                        resolved.runtimeSchemaHash()),
                schema, parameters, appliesTo);
    }

    private RuntimePolicyLayers runtimePolicies(
            Long modelId,
            String capabilityCode) {
        List<AiModelRuntimePolicy> policies =
                runtimePolicyService.listByModelId(modelId);
        AiModelRuntimePolicy model = findPolicy(policies, "");
        AiModelRuntimePolicy capability = findPolicy(
                policies, capabilityCode);
        return new RuntimePolicyLayers(
                policyLayer(model), policyLayer(capability));
    }

    private AiModelRuntimePolicy findPolicy(
            List<AiModelRuntimePolicy> policies,
            String capabilityCode) {
        return policies.stream()
                .filter(policy -> capabilityCode.equals(
                        normalizeStoredCode(policy.getCapabilityCode())))
                .findFirst()
                .orElse(null);
    }

    private RuntimePolicyLayer policyLayer(AiModelRuntimePolicy policy) {
        if (policy == null) {
            return RuntimePolicyLayer.empty();
        }
        return new RuntimePolicyLayer(
                policy.getMaxConcurrency(), policy.getConnectTimeoutMs(),
                policy.getReadTimeoutMs(), policy.getRetryCount(),
                policy.getQpsLimit(), policy.getPriority(),
                parseObject(policy.getExtraConfig(), "RuntimePolicy extraConfig"));
    }

    private RuntimeProfileVersions profileVersions(
            ProviderConnectionVO connection,
            String modelName,
            SchemaProfileDefinition providerProfile) {
        SchemaProfileDefinition protocolProfile = profileRegistry.findLatest(
                        SchemaProfileDefinition.ProfileLayer.PROTOCOL,
                        connection.protocolCode())
                .orElseThrow(() -> new ServiceException(
                        "SCHEMA_PROFILE_MISSING: " + connection.protocolCode()));
        Integer modelVersion = profileRegistry.matchModel(
                        connection.providerCode(), modelName)
                .map(SchemaProfileDefinition::profileVersion)
                .orElse(null);
        return new RuntimeProfileVersions(
                protocolProfile.profileVersion(),
                providerProfile.profileVersion(), modelVersion);
    }

    private SchemaReference storedReference(AiModelCapability row) {
        String capabilityCode = normalizeStoredCode(row.getCapabilityCode());
        if (!CODE_PATTERN.matcher(capabilityCode).matches()
                || row.getSchemaVersion() == null
                || row.getSchemaVersion() < 1) {
            throw new ServiceException(
                    "SCHEMA_VERSION_MISMATCH: " + capabilityCode);
        }
        try {
            return new SchemaReference(capabilityCode,
                    row.getSchemaVersion(), row.getSchemaHash());
        } catch (Exception e) {
            throw new ServiceException(
                    "SCHEMA_HASH_MISMATCH: " + capabilityCode);
        }
    }

    private ObjectNode parseObject(String json, String label) {
        if (json == null || json.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new ServiceException(label + " 内容过大");
        }
        try {
            JsonNode value = objectMapper.readTree(json);
            if (!value.isObject()) {
                throw new ServiceException(label + " 必须是 JSON object");
            }
            return (ObjectNode) value;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException(label + " JSON 格式无效");
        }
    }

    private void validateModel(AiModelConfig model) {
        if (!"1".equals(model.getStatus())) {
            throw new ServiceException("MODEL_DISABLED");
        }
        if (model.getId() == null || model.getConnectionId() == null
                || model.getRevision() == null || model.getRevision() < 1
                || blank(model.getModelCode()) || blank(model.getModelName())) {
            throw new ServiceException("MODEL_CONFIG_INVALID");
        }
    }

    private void validateConnection(ProviderConnectionVO connection) {
        if (connection == null || !"1".equals(connection.status())) {
            throw new ServiceException("PROVIDER_CONNECTION_DISABLED");
        }
        if (connection.id() == null || connection.revision() < 1
                || blank(connection.providerCode())
                || blank(connection.protocolCode())
                || blank(connection.networkMode())
                || blank(connection.baseUrl())) {
            throw new ServiceException("PROVIDER_CONNECTION_INVALID");
        }
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            throw new ServiceException("CAPABILITY_NOT_ENABLED");
        }
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new ServiceException("CAPABILITY_NOT_ENABLED");
        }
        return code;
    }

    private String normalizeStoredCode(String value) {
        return value == null ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void prune(long now) {
        cache.entrySet().removeIf(entry ->
                entry.getValue().expiresAtMillis() <= now);
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
    }

    private record DefinitionCacheKey(
            Long modelId,
            long modelRevision,
            Long connectionId,
            long connectionRevision,
            String capabilityCode,
            String schemaHash) {

        private DefinitionCacheKey {
            Objects.requireNonNull(modelId, "modelId");
            Objects.requireNonNull(connectionId, "connectionId");
        }
    }

    private record CacheEntry(
            ResolvedModelDefinition definition,
            long expiresAtMillis) {
    }
}
