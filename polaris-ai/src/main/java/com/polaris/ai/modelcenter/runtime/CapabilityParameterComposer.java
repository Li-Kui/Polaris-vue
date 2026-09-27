package com.polaris.ai.modelcenter.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.modelcenter.schema.ParameterPolicyDefinition;
import com.polaris.ai.modelcenter.schema.ParameterPolicyEnforcer;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** 按可信 ParameterPolicy 组合模型默认参数和请求级多来源覆盖。 */
@Component
public class CapabilityParameterComposer {

    private static final int MAX_OVERRIDE_FIELDS = 128;
    private static final int MAX_OVERRIDE_BYTES = 65_536;

    private final ParameterPolicyEnforcer policyEnforcer;
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    public CapabilityParameterComposer(
            ParameterPolicyEnforcer policyEnforcer) {
        this.policyEnforcer = policyEnforcer;
    }

    public ComposedCapabilityParameters compose(
            ResolvedModelDefinition definition,
            Set<String> activeFeatures,
            List<CapabilityParameterOverride> overrides) {
        if (definition == null) {
            throw new ServiceException("MODEL_DEFINITION_REQUIRED");
        }
        Set<String> active = normalizeFeatures(activeFeatures);
        for (String feature : active) {
            if (!definition.enabledFeatures().containsKey(feature)) {
                throw new ServiceException("FEATURE_NOT_ENABLED: " + feature);
            }
        }
        ObjectNode invocation = definition.invocation().parameters();
        Map<String, ObjectNode> features = new LinkedHashMap<>();
        active.stream().sorted().forEach(code -> features.put(
                code, definition.enabledFeatures().get(code).parameters()));

        EnumMap<ParameterPolicyDefinition.ParameterSource,
                CapabilityParameterOverride> ordered = order(overrides);
        for (ParameterPolicyDefinition.ParameterSource source
                : ParameterPolicyDefinition.ParameterSource.values()) {
            CapabilityParameterOverride override = ordered.get(source);
            if (override == null) {
                continue;
            }
            invocation = apply(definition.invocation(), invocation,
                    override.invocation(), source);
            for (Map.Entry<String, Map<String, Object>> item
                    : override.features().entrySet()) {
                String featureCode = normalizeCode(item.getKey());
                ResolvedCapabilityDefinition feature =
                        definition.enabledFeatures().get(featureCode);
                if (feature == null || !active.contains(featureCode)) {
                    throw new ServiceException(
                            "FEATURE_NOT_ACTIVE: " + featureCode);
                }
                features.put(featureCode, apply(
                        feature, features.get(featureCode),
                        item.getValue(), source));
            }
        }
        return new ComposedCapabilityParameters(invocation, features);
    }

    private ObjectNode apply(
            ResolvedCapabilityDefinition definition,
            ObjectNode base,
            Map<String, Object> override,
            ParameterPolicyDefinition.ParameterSource source) {
        if (override == null || override.isEmpty()) {
            return base;
        }
        if (override.size() > MAX_OVERRIDE_FIELDS) {
            throw new ServiceException("CAPABILITY_CONFIG_INVALID: 覆盖字段过多");
        }
        JsonNode value = objectMapper.valueToTree(override);
        if (value.toString().getBytes(StandardCharsets.UTF_8).length
                > MAX_OVERRIDE_BYTES) {
            throw new ServiceException("CAPABILITY_CONFIG_INVALID: 覆盖内容过大");
        }
        return policyEnforcer.apply(
                definition.schema(), base, value, source);
    }

    private EnumMap<ParameterPolicyDefinition.ParameterSource,
            CapabilityParameterOverride> order(
            List<CapabilityParameterOverride> overrides) {
        EnumMap<ParameterPolicyDefinition.ParameterSource,
                CapabilityParameterOverride> result = new EnumMap<>(
                ParameterPolicyDefinition.ParameterSource.class);
        for (CapabilityParameterOverride override
                : overrides == null ? List.<CapabilityParameterOverride>of()
                : new ArrayList<>(overrides)) {
            if (override == null
                    || result.putIfAbsent(override.source(), override) != null) {
                throw new ServiceException(
                        "MODEL_CONFIG_INVALID: Override Source 重复或为空");
            }
        }
        return result;
    }

    private Set<String> normalizeFeatures(Set<String> features) {
        Set<String> result = new TreeSet<>();
        if (features != null) {
            features.forEach(feature -> result.add(normalizeCode(feature)));
        }
        return Set.copyOf(result);
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            throw new ServiceException("FEATURE_NOT_ENABLED");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
