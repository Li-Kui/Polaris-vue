package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.common.exception.ServiceException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.*;

/** 启动时一次性加载的 classpath Capability Schema Registry。 */
@Component
public class ClasspathCapabilitySchemaRegistry implements CapabilitySchemaRegistry {

    static final String RESOURCE_PATTERN =
            "classpath*:model-schema/capability/*/v*.json";

    private final Map<CapabilitySchemaKey, CapabilitySchemaDefinition> definitions;
    private final Map<String, CapabilitySchemaDefinition> latestByCode;
    private final List<CapabilitySchemaDefinition> orderedDefinitions;

    public ClasspathCapabilitySchemaRegistry() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(RESOURCE_PATTERN);
            if (resources.length == 0) {
                throw new IllegalStateException("Capability Schema 资源不能为空");
            }
            ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
            this.definitions = new CapabilitySchemaResourceLoader(objectMapper)
                    .load(resources);
            this.orderedDefinitions = definitions.values().stream()
                    .sorted(Comparator.comparing(CapabilitySchemaDefinition::code)
                            .thenComparingInt(CapabilitySchemaDefinition::schemaVersion))
                    .toList();
            Map<String, CapabilitySchemaDefinition> latest = new java.util.LinkedHashMap<>();
            orderedDefinitions.forEach(definition -> latest.merge(
                    definition.code(), definition,
                    (left, right) -> left.schemaVersion() >= right.schemaVersion()
                            ? left : right));
            this.latestByCode = Map.copyOf(latest);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Capability Schema Registry 初始化失败", e);
        }
    }

    @Override
    public Optional<CapabilitySchemaDefinition> find(String code, int version) {
        if (code == null || code.isBlank() || version < 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(new CapabilitySchemaKey(code, version)));
    }

    @Override
    public CapabilitySchemaDefinition getRequired(String code, int version) {
        return find(code, version).orElseThrow(() -> new ServiceException(
                "Capability Schema 不存在: " + normalize(code) + "@v" + version));
    }

    @Override
    public Optional<CapabilitySchemaDefinition> findLatest(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(latestByCode.get(normalize(code)));
    }

    @Override
    public CapabilitySchemaDefinition getLatestRequired(String code) {
        return findLatest(code).orElseThrow(() -> new ServiceException(
                "Capability Schema 不存在: " + normalize(code)));
    }

    @Override
    public List<CapabilitySchemaDefinition> list() {
        return orderedDefinitions;
    }

    private String normalize(String code) {
        return code == null ? "null" : code.trim().toUpperCase(Locale.ROOT);
    }
}
