package com.polaris.ai.modelcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.domain.AiModelCapability;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.domain.AiModelRuntimePolicy;
import com.polaris.ai.mapper.AiModelCapabilityMapper;
import com.polaris.ai.mapper.AiModelConfigMapper;
import com.polaris.ai.mapper.AiModelRuntimePolicyMapper;
import com.polaris.ai.modelcenter.dto.ModelAggregateSaveRequest;
import com.polaris.ai.modelcenter.dto.ModelCapabilitySaveRequest;
import com.polaris.ai.modelcenter.dto.ModelRuntimePolicySaveRequest;
import com.polaris.ai.modelcenter.schema.*;
import com.polaris.ai.modelcenter.vo.*;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Model Aggregate 的唯一读写入口。 */
@Service
public class ModelAggregateService {

    private static final Pattern MODEL_CODE =
            Pattern.compile("^[a-z0-9][a-z0-9._-]{0,99}$");
    private static final Pattern CAPABILITY_CODE =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");
    private static final int MAX_JSON_BYTES = 65_536;

    private final AiModelConfigMapper modelMapper;
    private final AiModelCapabilityMapper capabilityMapper;
    private final AiModelRuntimePolicyMapper policyMapper;
    private final ModelAggregateAccessGuard accessGuard;
    private final IAiProviderConnectionService connectionService;
    private final CapabilitySchemaRegistry schemaRegistry;
    private final SchemaProfileResolver schemaResolver;
    private final SchemaProfileRegistry profileRegistry;
    private final CapabilityConfigProcessor configProcessor;
    private final ModelReferenceService referenceService;
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    public ModelAggregateService(
            AiModelConfigMapper modelMapper,
            AiModelCapabilityMapper capabilityMapper,
            AiModelRuntimePolicyMapper policyMapper,
            ModelAggregateAccessGuard accessGuard,
            IAiProviderConnectionService connectionService,
            CapabilitySchemaRegistry schemaRegistry,
            SchemaProfileResolver schemaResolver,
            SchemaProfileRegistry profileRegistry,
            CapabilityConfigProcessor configProcessor,
            ModelReferenceService referenceService) {
        this.modelMapper = modelMapper;
        this.capabilityMapper = capabilityMapper;
        this.policyMapper = policyMapper;
        this.accessGuard = accessGuard;
        this.connectionService = connectionService;
        this.schemaRegistry = schemaRegistry;
        this.schemaResolver = schemaResolver;
        this.profileRegistry = profileRegistry;
        this.configProcessor = configProcessor;
        this.referenceService = referenceService;
    }

    public ModelEditorContextVO editorContext() {
        List<ProviderConnectionVO> connections = connectionService.list(
                new com.polaris.ai.modelcenter.dto.ProviderConnectionQuery());
        return new ModelEditorContextVO(
                connections,
                schemaRegistry.list().stream()
                        .map(CapabilitySchemaEditorVO::from)
                        .toList(),
                profileRegistry.list().stream()
                        .filter(profile -> profile.layer()
                                == SchemaProfileDefinition.ProfileLayer.PROVIDER)
                        .map(ProviderProfileEditorVO::from)
                        .toList());
    }

    @Transactional(readOnly = true)
    public List<ModelSummaryVO> list() {
        List<AiModelConfig> models = modelMapper.selectAvailableModelConfigs(
                CallerUtils.getDeptId(), CallerUtils.isSuperAdmin());
        return models.stream().map(model -> {
            ProviderConnectionVO connection = connectionService.get(
                    model.getConnectionId());
            List<String> capabilities = capabilityMapper.selectList(
                    new LambdaQueryWrapper<AiModelCapability>()
                            .eq(AiModelCapability::getModelConfigId, model.getId())
                            .eq(AiModelCapability::getEnabled, "1")
                            .orderByAsc(AiModelCapability::getCapabilityCode))
                    .stream().map(row -> capabilityKey(
                            row.getCapabilityCode(),
                            row.getAppliesToCapabilityCode())).toList();
            return new ModelSummaryVO(
                    model.getId(), model.getName(), model.getModelCode(),
                    model.getConnectionId(), connection.connectionName(),
                    model.getModelName(), model.getModelType(),
                    model.getDescription(),
                    model.getRevision() == null ? 1L : model.getRevision(),
                    model.getStatus(), capabilities);
        }).toList();
    }

    @Transactional(readOnly = true)
    public ModelAggregateVO get(Long modelId) {
        AiModelConfig model = accessGuard.requireAccessible(modelId);
        ProviderConnectionVO connection = connectionService.get(
                model.getConnectionId());
        List<AiModelCapability> capabilities = capabilityMapper.selectList(
                new LambdaQueryWrapper<AiModelCapability>()
                        .eq(AiModelCapability::getModelConfigId, modelId)
                        .orderByAsc(AiModelCapability::getCapabilityCode)
                        .orderByAsc(AiModelCapability::getAppliesToCapabilityCode));
        List<AiModelRuntimePolicy> policies = policyMapper.selectList(
                new LambdaQueryWrapper<AiModelRuntimePolicy>()
                        .eq(AiModelRuntimePolicy::getModelConfigId, modelId)
                        .orderByAsc(AiModelRuntimePolicy::getCapabilityCode));
        return toView(model, connection, capabilities, policies);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ModelAggregateSaveRequest request) {
        ValidatedRequest validated = validate(request);
        AiModelConfig model = new AiModelConfig();
        model.setTenantId(currentTenantId());
        model.setDeptId(validated.deptId());
        model.setName(validated.name());
        model.setModelCode(validated.modelCode());
        model.setConnectionId(validated.connection().id());
        model.setModelName(validated.modelName());
        model.setModelType(validated.modelType());
        model.setDescription(validated.description());
        model.setRevision(1L);
        model.setStatus(validated.status());
        model.setDelFlag("0");
        model.setCreateBy(CallerUtils.getUsername());
        model.setUpdateBy(CallerUtils.getUsername());
        model.setCreateTime(new Date());
        model.setUpdateTime(new Date());
        if (modelMapper.insert(model) != 1) {
            throw new ServiceException("MODEL_SAVE_FAILED");
        }
        saveChildren(model.getId(), validated);
        return model.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long modelId, ModelAggregateSaveRequest request) {
        accessGuard.requireAccessible(modelId);
        if (request == null || request.expectedRevision() == null
                || !Objects.equals(request.id(), modelId)) {
            throw new ServiceException("MODEL_EXPECTED_REVISION_REQUIRED");
        }
        ValidatedRequest validated = validate(request);
        if (modelMapper.updateAggregateBase(
                modelId, request.expectedRevision(), validated.name(),
                validated.modelCode(), validated.connection().id(),
                validated.modelName(), validated.modelType(),
                validated.description(), validated.deptId(),
                validated.status(), CallerUtils.getUsername()) != 1) {
            throw new ServiceException("MODEL_CONFIG_CONFLICT");
        }
        saveChildren(modelId, validated);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long modelId) {
        accessGuard.requireAccessible(modelId);
        referenceService.assertNotReferenced(modelId);
        AiModelConfig deleted = new AiModelConfig();
        deleted.setId(modelId);
        deleted.setDelFlag("2");
        deleted.setUpdateBy(CallerUtils.getUsername());
        deleted.setUpdateTime(new Date());
        if (modelMapper.updateById(deleted) != 1) {
            throw new ServiceException("MODEL_DELETE_FAILED");
        }
    }

    private void saveChildren(Long modelId, ValidatedRequest request) {
        Map<String, AiModelCapability> existingCapabilities =
                new LinkedHashMap<>();
        capabilityMapper.selectList(new LambdaQueryWrapper<AiModelCapability>()
                        .eq(AiModelCapability::getModelConfigId, modelId))
                .forEach(row -> existingCapabilities.put(
                        capabilityKey(row.getCapabilityCode(),
                                row.getAppliesToCapabilityCode()), row));
        for (ValidatedCapability item : request.capabilities()) {
            String key = capabilityKey(item.code(), item.appliesTo());
            AiModelCapability row = existingCapabilities.get(key);
            if (row == null) {
                row = new AiModelCapability();
                row.setModelConfigId(modelId);
                row.setCapabilityCode(item.code());
                row.setAppliesToCapabilityCode(item.appliesTo());
                row.setCreateTime(new Date());
            }
            row.setSchemaVersion(item.schemaVersion());
            row.setSchemaHash(item.schemaHash());
            row.setConfigJson(writeJson(item.config()));
            row.setEnabled(item.enabled() ? "1" : "0");
            row.setCapabilitySource("MANUAL");
            row.setUpdateTime(new Date());
            int affected = row.getId() == null
                    ? capabilityMapper.insert(row)
                    : capabilityMapper.updateById(row);
            if (affected != 1) {
                throw new ServiceException("MODEL_CAPABILITY_SAVE_FAILED");
            }
        }

        Map<String, AiModelRuntimePolicy> existingPolicies =
                new LinkedHashMap<>();
        policyMapper.selectList(new LambdaQueryWrapper<AiModelRuntimePolicy>()
                        .eq(AiModelRuntimePolicy::getModelConfigId, modelId))
                .forEach(row -> existingPolicies.put(
                        normalizePolicyCode(row.getCapabilityCode()), row));
        for (ModelRuntimePolicySaveRequest item : request.policies()) {
            String code = normalizePolicyCode(item.capabilityCode());
            AiModelRuntimePolicy row = existingPolicies.get(code);
            if (row == null) {
                row = new AiModelRuntimePolicy();
                row.setModelConfigId(modelId);
                row.setCapabilityCode(code);
                row.setCreateTime(new Date());
            }
            row.setMaxConcurrency(item.maxConcurrency());
            row.setConnectTimeoutMs(item.connectTimeoutMs());
            row.setReadTimeoutMs(item.readTimeoutMs());
            row.setRetryCount(item.retryCount());
            row.setQpsLimit(item.qpsLimit());
            row.setPriority(item.priority());
            row.setExtraConfig(writeJson(item.extraConfig()));
            row.setUpdateTime(new Date());
            int affected = row.getId() == null
                    ? policyMapper.insert(row) : policyMapper.updateById(row);
            if (affected != 1) {
                throw new ServiceException("MODEL_RUNTIME_POLICY_SAVE_FAILED");
            }
        }
    }

    private ValidatedRequest validate(ModelAggregateSaveRequest request) {
        if (request == null) {
            throw new ServiceException("MODEL_SAVE_REQUEST_REQUIRED");
        }
        String name = requireText(request.name(), 100, "MODEL_NAME_REQUIRED");
        String modelCode = request.modelCode() == null ? ""
                : request.modelCode().trim().toLowerCase(Locale.ROOT);
        if (!MODEL_CODE.matcher(modelCode).matches()) {
            throw new ServiceException("MODEL_CODE_INVALID");
        }
        if (request.connectionId() == null) {
            throw new ServiceException("MODEL_CONNECTION_REQUIRED");
        }
        ProviderConnectionVO connection = connectionService.get(
                request.connectionId());
        if (!"1".equals(connection.status())) {
            throw new ServiceException("PROVIDER_CONNECTION_DISABLED");
        }
        String modelName = requireText(
                request.modelName(), 256, "REMOTE_MODEL_NAME_REQUIRED");
        String modelType = request.modelType() == null
                ? "GENERAL" : request.modelType().trim().toUpperCase(Locale.ROOT);
        if (!modelType.matches("[A-Z][A-Z0-9_-]{0,31}")) {
            throw new ServiceException("MODEL_TYPE_INVALID");
        }
        String status = "0".equals(request.status()) ? "0" : "1";
        Long deptId = CallerUtils.isPlatformMode() ? null : request.deptId();
        List<ValidatedCapability> capabilities = new ArrayList<>();
        Map<String, Boolean> unique = new LinkedHashMap<>();
        for (ModelCapabilitySaveRequest item : request.capabilities()) {
            ValidatedCapability capability = validateCapability(
                    item, connection, modelName);
            if (unique.putIfAbsent(capabilityKey(
                    capability.code(), capability.appliesTo()), true) != null) {
                throw new ServiceException("MODEL_CAPABILITY_DUPLICATE");
            }
            capabilities.add(capability);
        }
        if (capabilities.stream().noneMatch(
                item -> item.enabled()
                        && item.kind()
                        == CapabilitySchemaDefinition.CapabilityKind.INVOCATION)) {
            throw new ServiceException("MODEL_INVOCATION_CAPABILITY_REQUIRED");
        }
        validatePolicies(request.runtimePolicies());
        return new ValidatedRequest(
                name, modelCode, connection, modelName, modelType,
                trimToNull(request.description()), deptId, status,
                List.copyOf(capabilities), request.runtimePolicies());
    }

    private ValidatedCapability validateCapability(
            ModelCapabilitySaveRequest item,
            ProviderConnectionVO connection,
            String modelName) {
        if (item == null) {
            throw new ServiceException("MODEL_CAPABILITY_REQUIRED");
        }
        String code = normalizeCapabilityCode(item.capabilityCode());
        int version = item.schemaVersion() == null
                ? schemaRegistry.getLatestRequired(code).schemaVersion()
                : item.schemaVersion();
        ResolvedCapabilitySchema resolved = schemaResolver.resolve(
                code, version, connection.protocolCode(),
                connection.providerCode(), modelName);
        String rawAppliesTo = item.appliesToCapabilityCode();
        String appliesTo = rawAppliesTo == null || rawAppliesTo.isBlank()
                ? "" : normalizeCapabilityCode(rawAppliesTo);
        CapabilitySchemaDefinition definition = resolved.definition();
        if (definition.kind()
                == CapabilitySchemaDefinition.CapabilityKind.INVOCATION
                && !appliesTo.isEmpty()) {
            throw new ServiceException("INVOCATION_APPLIES_TO_NOT_ALLOWED");
        }
        if (definition.kind()
                == CapabilitySchemaDefinition.CapabilityKind.FEATURE
                && (appliesTo.isEmpty()
                || !definition.allowedAppliesTo().contains(appliesTo))) {
            throw new ServiceException("FEATURE_APPLIES_TO_INVALID");
        }
        ObjectNode config = configProcessor.process(
                definition, objectMapper.valueToTree(item.config()));
        requireJsonSize(config);
        return new ValidatedCapability(
                code, appliesTo, version, resolved.runtimeSchemaHash(),
                !Boolean.FALSE.equals(item.enabled()), config,
                definition.kind());
    }

    private void validatePolicies(List<ModelRuntimePolicySaveRequest> policies) {
        Map<String, Boolean> unique = new LinkedHashMap<>();
        for (ModelRuntimePolicySaveRequest policy : policies) {
            if (policy == null) {
                throw new ServiceException("MODEL_RUNTIME_POLICY_INVALID");
            }
            String code = normalizePolicyCode(policy.capabilityCode());
            if (unique.putIfAbsent(code, true) != null) {
                throw new ServiceException("MODEL_RUNTIME_POLICY_DUPLICATE");
            }
            range(policy.maxConcurrency(), 1, 10_000, "maxConcurrency");
            range(policy.connectTimeoutMs(), 100, 60_000,
                    "connectTimeoutMs");
            range(policy.readTimeoutMs(), 100, 300_000, "readTimeoutMs");
            range(policy.retryCount(), 0, 10, "retryCount");
            range(policy.priority(), -10_000, 10_000, "priority");
            if (policy.qpsLimit() != null
                    && (policy.qpsLimit().signum() <= 0
                    || policy.qpsLimit().compareTo(
                    new java.math.BigDecimal("10000")) > 0)) {
                throw new ServiceException(
                        "MODEL_RUNTIME_POLICY_INVALID: qpsLimit");
            }
            requireJsonSize(objectMapper.valueToTree(policy.extraConfig()));
        }
    }

    private ModelAggregateVO toView(
            AiModelConfig model,
            ProviderConnectionVO connection,
            List<AiModelCapability> capabilities,
            List<AiModelRuntimePolicy> policies) {
        List<ModelCapabilityVO> capabilityViews = capabilities.stream()
                .map(row -> {
                    ResolvedCapabilitySchema resolved = schemaResolver.resolve(
                            row.getCapabilityCode(), row.getSchemaVersion(),
                            connection.protocolCode(), connection.providerCode(),
                            model.getModelName());
                    return new ModelCapabilityVO(
                            capabilityKey(row.getCapabilityCode(),
                                    row.getAppliesToCapabilityCode()),
                            row.getCapabilityCode(),
                            row.getAppliesToCapabilityCode(),
                            row.getSchemaVersion(), row.getSchemaHash(),
                            "1".equals(row.getEnabled()),
                            row.getCapabilitySource(),
                            readMap(row.getConfigJson()),
                            resolved.definition());
                }).toList();
        List<ModelRuntimePolicyVO> policyViews = policies.stream()
                .map(row -> new ModelRuntimePolicyVO(
                        row.getCapabilityCode(), row.getMaxConcurrency(),
                        row.getConnectTimeoutMs(), row.getReadTimeoutMs(),
                        row.getRetryCount(), row.getQpsLimit(),
                        row.getPriority(), readMap(row.getExtraConfig())))
                .toList();
        return new ModelAggregateVO(
                model.getId(), model.getTenantId(), model.getDeptId(),
                model.getName(), model.getModelCode(), model.getConnectionId(),
                model.getModelName(), model.getModelType(),
                model.getDescription(),
                model.getRevision() == null ? 1L : model.getRevision(),
                model.getStatus(), capabilityViews, policyViews);
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new ServiceException("MODEL_CONFIG_INVALID: JSON 无效");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(
                    value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new ServiceException("MODEL_CONFIG_JSON_WRITE_FAILED");
        }
    }

    private void requireJsonSize(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.toString().getBytes(StandardCharsets.UTF_8).length
                > MAX_JSON_BYTES) {
            throw new ServiceException("MODEL_CONFIG_INVALID: JSON 过大");
        }
    }

    private String normalizeCapabilityCode(String value) {
        String code = value == null ? ""
                : value.trim().toUpperCase(Locale.ROOT);
        if (!CAPABILITY_CODE.matcher(code).matches()) {
            throw new ServiceException("CAPABILITY_CODE_INVALID");
        }
        return code;
    }

    private String normalizePolicyCode(String value) {
        return value == null || value.isBlank()
                ? "" : normalizeCapabilityCode(value);
    }

    private String capabilityKey(String code, String appliesTo) {
        String normalizedAppliesTo = appliesTo == null ? "" : appliesTo;
        return normalizedAppliesTo.isEmpty()
                ? code : code + "@" + normalizedAppliesTo;
    }

    private String requireText(String value, int max, String error) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new ServiceException(error);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void range(Integer value, int min, int max, String field) {
        if (value != null && (value < min || value > max)) {
            throw new ServiceException(
                    "MODEL_RUNTIME_POLICY_INVALID: " + field);
        }
    }

    private Long currentTenantId() {
        if (!CallerUtils.isPlatformMode()) {
            return null;
        }
        try {
            long value = Long.parseLong(CallerUtils.getTenantId());
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (Exception e) {
            throw new ServiceException("TENANT_CONTEXT_REQUIRED");
        }
    }

    private record ValidatedCapability(
            String code,
            String appliesTo,
            int schemaVersion,
            String schemaHash,
            boolean enabled,
            ObjectNode config,
            CapabilitySchemaDefinition.CapabilityKind kind) {
    }

    private record ValidatedRequest(
            String name,
            String modelCode,
            ProviderConnectionVO connection,
            String modelName,
            String modelType,
            String description,
            Long deptId,
            String status,
            List<ValidatedCapability> capabilities,
            List<ModelRuntimePolicySaveRequest> policies) {
    }
}
