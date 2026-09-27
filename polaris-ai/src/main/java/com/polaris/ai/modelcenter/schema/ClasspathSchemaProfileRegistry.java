package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.modelcenter.schema.options.SchemaOptionsResolverRegistry;
import com.polaris.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.*;

/** 加载、编译并冻结全部 Protocol / Provider / Model Profile。 */
@Component
public class ClasspathSchemaProfileRegistry implements SchemaProfileRegistry {

    static final List<String> RESOURCE_PATTERNS = List.of(
            "classpath*:model-schema/protocol/*/v*.json",
            "classpath*:model-schema/provider/*/v*.json",
            "classpath*:model-schema/model/*/v*.json");

    private final Map<SchemaProfileKey, SchemaProfileDefinition> definitions;
    private final Map<LayerCode, SchemaProfileDefinition> latestByCode;
    private final List<SchemaProfileDefinition> latestModelProfiles;
    private final List<SchemaProfileDefinition> orderedDefinitions;
    private final SafeModelNameMatcher modelNameMatcher = new SafeModelNameMatcher();

    @Autowired
    public ClasspathSchemaProfileRegistry(
            CapabilitySchemaRegistry capabilityRegistry,
            CapabilitySchemaResolver capabilityResolver,
            ModelProfileTighteningValidator tighteningValidator,
            SchemaOptionsResolverRegistry optionsRegistry) {
        this(loadResources(), capabilityRegistry, capabilityResolver,
                tighteningValidator, optionsRegistry);
    }

    ClasspathSchemaProfileRegistry(
            Resource[] resources,
            CapabilitySchemaRegistry capabilityRegistry,
            CapabilitySchemaResolver capabilityResolver,
            ModelProfileTighteningValidator tighteningValidator,
            SchemaOptionsResolverRegistry optionsRegistry) {
        List<SchemaProfileDefinition> loaded = new SchemaProfileResourceLoader(
                new ObjectMapper().findAndRegisterModules()).load(resources);
        Map<SchemaProfileKey, SchemaProfileDefinition> byKey = new LinkedHashMap<>();
        loaded.forEach(definition -> byKey.put(new SchemaProfileKey(
                definition.layer(), definition.code(),
                definition.profileVersion()), definition));
        this.definitions = Map.copyOf(byKey);
        this.orderedDefinitions = loaded.stream()
                .sorted(Comparator.comparing(
                                (SchemaProfileDefinition value) -> value.layer().name())
                        .thenComparing(SchemaProfileDefinition::code)
                        .thenComparingInt(SchemaProfileDefinition::profileVersion))
                .toList();

        Map<LayerCode, SchemaProfileDefinition> latest = new LinkedHashMap<>();
        orderedDefinitions.forEach(definition -> latest.merge(
                new LayerCode(definition.layer(), normalize(definition.code())),
                definition,
                (left, right) -> left.profileVersion() >= right.profileVersion()
                        ? left : right));
        this.latestByCode = Map.copyOf(latest);
        this.latestModelProfiles = latestByCode.values().stream()
                .filter(profile -> profile.layer()
                        == SchemaProfileDefinition.ProfileLayer.MODEL)
                .sorted(Comparator.comparing(SchemaProfileDefinition::code))
                .toList();

        validateModelConflicts();
        compileContracts(capabilityRegistry, capabilityResolver,
                tighteningValidator, optionsRegistry);
    }

    @Override
    public Optional<SchemaProfileDefinition> find(
            SchemaProfileDefinition.ProfileLayer layer,
            String code,
            int version) {
        if (layer == null || code == null || code.isBlank() || version < 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(
                new SchemaProfileKey(layer, code, version)));
    }

    @Override
    public Optional<SchemaProfileDefinition> findLatest(
            SchemaProfileDefinition.ProfileLayer layer,
            String code) {
        if (layer == null || code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(latestByCode.get(
                new LayerCode(layer, normalize(code))));
    }

    @Override
    public Optional<SchemaProfileDefinition> matchModel(
            String providerCode,
            String modelName) {
        String provider = normalize(providerCode);
        List<SchemaProfileDefinition> matches = latestModelProfiles.stream()
                .filter(profile -> normalize(profile.providerCode()).equals(provider))
                .filter(profile -> modelNameMatcher.matches(
                        profile.modelNamePattern(), modelName))
                .sorted(Comparator.comparingInt(
                                (SchemaProfileDefinition profile) ->
                                        modelNameMatcher.priority(
                                                profile.modelNamePattern()))
                        .reversed())
                .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        int priority = modelNameMatcher.priority(matches.get(0).modelNamePattern());
        if (matches.size() > 1 && modelNameMatcher.priority(
                matches.get(1).modelNamePattern()) == priority) {
            throw new ServiceException("Model Profile 匹配冲突: " + modelName);
        }
        return Optional.of(matches.get(0));
    }

    @Override
    public List<SchemaProfileDefinition> list() {
        return orderedDefinitions;
    }

    private void compileContracts(
            CapabilitySchemaRegistry capabilityRegistry,
            CapabilitySchemaResolver capabilityResolver,
            ModelProfileTighteningValidator tighteningValidator,
            SchemaOptionsResolverRegistry optionsRegistry) {
        capabilityRegistry.list().forEach(definition ->
                validateOptions(definition, optionsRegistry));
        orderedDefinitions.forEach(profile -> profile.capabilityOverlays().keySet()
                .forEach(capability -> capabilityRegistry.getLatestRequired(capability)));
        orderedDefinitions.stream()
                .filter(profile -> profile.layer()
                        == SchemaProfileDefinition.ProfileLayer.PROVIDER)
                .flatMap(profile -> profile.supportedCapabilities().stream())
                .forEach(capabilityRegistry::getLatestRequired);

        orderedDefinitions.forEach(profile -> compileProfile(
                profile, capabilityRegistry, capabilityResolver,
                tighteningValidator, optionsRegistry));
    }

    private void compileProfile(
            SchemaProfileDefinition profile,
            CapabilitySchemaRegistry capabilityRegistry,
            CapabilitySchemaResolver capabilityResolver,
            ModelProfileTighteningValidator tighteningValidator,
            SchemaOptionsResolverRegistry optionsRegistry) {
        for (String capability : profile.capabilityOverlays().keySet()) {
            CapabilitySchemaDefinition base =
                    capabilityRegistry.getLatestRequired(capability);
            SchemaProfileDefinition protocol = protocolFor(profile).orElse(null);
            SchemaProfileDefinition provider = providerFor(profile).orElse(null);
            JsonNodes overlays = new JsonNodes(
                    overlay(protocol, capability),
                    overlay(provider, capability),
                    profile.layer() == SchemaProfileDefinition.ProfileLayer.MODEL
                            ? profile.overlay(capability) : null);
            ResolvedCapabilitySchema beforeModel = capabilityResolver.resolve(
                    base, overlays.protocol(), overlays.provider(), null);
            ResolvedCapabilitySchema resolved = capabilityResolver.resolve(
                    base, overlays.protocol(), overlays.provider(), overlays.model());
            if (profile.layer() == SchemaProfileDefinition.ProfileLayer.MODEL
                    && !profile.allowConstraintRelaxation()) {
                tighteningValidator.validate(
                        beforeModel.definition(), resolved.definition());
            }
            validateOptions(resolved.definition(), optionsRegistry);
        }
    }

    private Optional<SchemaProfileDefinition> protocolFor(
            SchemaProfileDefinition profile) {
        if (profile.layer() == SchemaProfileDefinition.ProfileLayer.PROTOCOL) {
            return Optional.of(profile);
        }
        return Optional.empty();
    }

    private Optional<SchemaProfileDefinition> providerFor(
            SchemaProfileDefinition profile) {
        if (profile.layer() == SchemaProfileDefinition.ProfileLayer.PROVIDER) {
            return Optional.of(profile);
        }
        if (profile.layer() == SchemaProfileDefinition.ProfileLayer.MODEL) {
            return Optional.of(findLatest(
                    SchemaProfileDefinition.ProfileLayer.PROVIDER,
                    profile.providerCode()).orElseThrow(() ->
                    new IllegalStateException(
                            "Model Profile 引用不存在 Provider: "
                                    + profile.providerCode())));
        }
        return Optional.empty();
    }

    private com.fasterxml.jackson.databind.JsonNode overlay(
            SchemaProfileDefinition profile,
            String capability) {
        return profile == null ? null : profile.overlay(capability);
    }

    private void validateOptions(
            CapabilitySchemaDefinition definition,
            SchemaOptionsResolverRegistry registry) {
        definition.uiSchema().forEach((field, ui) -> {
            if (ui.optionsResolver() != null && !ui.optionsResolver().isBlank()
                    && !registry.contains(ui.optionsResolver())) {
                throw new IllegalStateException(
                        "未知 optionsResolver: " + ui.optionsResolver()
                                + " (" + definition.code() + "." + field + ")");
            }
        });
    }

    private void validateModelConflicts() {
        for (int leftIndex = 0; leftIndex < latestModelProfiles.size(); leftIndex++) {
            SchemaProfileDefinition left = latestModelProfiles.get(leftIndex);
            for (int rightIndex = leftIndex + 1;
                    rightIndex < latestModelProfiles.size(); rightIndex++) {
                SchemaProfileDefinition right = latestModelProfiles.get(rightIndex);
                if (!normalize(left.providerCode()).equals(
                        normalize(right.providerCode()))) {
                    continue;
                }
                int leftPriority = modelNameMatcher.priority(left.modelNamePattern());
                int rightPriority = modelNameMatcher.priority(right.modelNamePattern());
                if (leftPriority == rightPriority
                        && modelNameMatcher.overlaps(
                        left.modelNamePattern(), right.modelNamePattern())) {
                    throw new IllegalStateException(
                            "同优先级 Model Profile 冲突: " + left.code()
                                    + " / " + right.code());
                }
            }
        }
    }

    private static Resource[] loadResources() {
        try {
            List<Resource> resources = new ArrayList<>();
            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();
            for (String pattern : RESOURCE_PATTERNS) {
                resources.addAll(List.of(resolver.getResources(pattern)));
            }
            if (resources.isEmpty()) {
                throw new IllegalStateException("Schema Profile 资源不能为空");
            }
            return resources.toArray(Resource[]::new);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Schema Profile 资源扫描失败", e);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record LayerCode(
            SchemaProfileDefinition.ProfileLayer layer,
            String code) {}

    private record JsonNodes(
            com.fasterxml.jackson.databind.JsonNode protocol,
            com.fasterxml.jackson.databind.JsonNode provider,
            com.fasterxml.jackson.databind.JsonNode model) {}
}
