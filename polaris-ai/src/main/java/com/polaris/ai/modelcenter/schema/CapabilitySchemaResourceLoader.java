package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 读取、解析并校验版本化 Capability Schema 资源。 */
final class CapabilitySchemaResourceLoader {

    private static final Pattern VERSION_FILE = Pattern.compile("^v([1-9][0-9]*)\\.json$");
    private static final Pattern RESOURCE_PATH = Pattern.compile(
            ".*model-schema/capability/([^/]+)/v[1-9][0-9]*\\.json.*");

    private final ObjectMapper objectMapper;
    private final CapabilitySchemaDefinitionValidator validator =
            new CapabilitySchemaDefinitionValidator();

    CapabilitySchemaResourceLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
    }

    Map<CapabilitySchemaKey, CapabilitySchemaDefinition> load(Resource[] resources) {
        try {
            List<Resource> ordered = List.of(resources).stream()
                    .sorted(Comparator.comparing(Resource::getDescription))
                    .toList();
            Map<CapabilitySchemaKey, CapabilitySchemaDefinition> definitions =
                    new LinkedHashMap<>();
            for (Resource resource : ordered) {
                CapabilitySchemaDefinition definition = read(resource);
                validator.validate(definition);
                validateResourceIdentity(resource, definition);
                CapabilitySchemaKey key = new CapabilitySchemaKey(
                        definition.code(), definition.schemaVersion());
                if (definitions.putIfAbsent(key, definition) != null) {
                    throw new IllegalStateException(
                            "重复 Capability Schema: " + key.code() + "@v" + key.version());
                }
            }
            validateFeatureTargets(definitions);
            return Map.copyOf(definitions);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Capability Schema Registry 加载失败", e);
        }
    }

    private CapabilitySchemaDefinition read(Resource resource) {
        try (InputStream input = resource.getInputStream()) {
            return objectMapper.readValue(input, CapabilitySchemaDefinition.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Capability Schema 解析失败: " + resource.getDescription(), e);
        }
    }

    private void validateResourceIdentity(
            Resource resource,
            CapabilitySchemaDefinition definition) {
        String filename = resource.getFilename();
        Matcher filenameMatcher = VERSION_FILE.matcher(filename == null ? "" : filename);
        if (!filenameMatcher.matches()) {
            throw new IllegalStateException(
                    "Capability Schema 文件必须命名为 v{version}.json: "
                            + resource.getDescription());
        }
        int pathVersion = Integer.parseInt(filenameMatcher.group(1));
        if (pathVersion != definition.schemaVersion()) {
            throw new IllegalStateException(
                    "Capability Schema 文件名与 schemaVersion 不一致: "
                            + resource.getDescription());
        }
        Matcher pathMatcher = RESOURCE_PATH.matcher(
                resource.getDescription().replace('\\', '/'));
        if (pathMatcher.matches()) {
            String expectedFolder = definition.code().toLowerCase(Locale.ROOT)
                    .replace('_', '-');
            if (!expectedFolder.equals(pathMatcher.group(1))) {
                throw new IllegalStateException(
                        "Capability Schema 目录与 code 不一致: "
                                + resource.getDescription());
            }
        }
    }

    private void validateFeatureTargets(
            Map<CapabilitySchemaKey, CapabilitySchemaDefinition> definitions) {
        Map<String, CapabilitySchemaDefinition.CapabilityKind> kinds =
                new LinkedHashMap<>();
        definitions.values().forEach(definition -> {
            CapabilitySchemaDefinition.CapabilityKind previous =
                    kinds.putIfAbsent(definition.code(), definition.kind());
            if (previous != null && previous != definition.kind()) {
                throw new IllegalStateException(
                        "Capability kind 在不同版本中不一致: " + definition.code());
            }
        });
        definitions.values().stream()
                .filter(definition -> definition.kind()
                        == CapabilitySchemaDefinition.CapabilityKind.FEATURE)
                .forEach(definition -> definition.allowedAppliesTo().forEach(target -> {
                    if (kinds.get(target)
                            != CapabilitySchemaDefinition.CapabilityKind.INVOCATION) {
                        throw new IllegalStateException(
                                "Feature allowedAppliesTo 未引用 Invocation Capability: "
                                        + definition.code() + " -> " + target);
                    }
                }));
    }
}
