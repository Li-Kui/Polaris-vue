package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 读取并执行 Profile 资源自身的结构和路径校验。 */
final class SchemaProfileResourceLoader {

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");
    private static final Pattern VERSION_FILE =
            Pattern.compile("^v([1-9][0-9]*)\\.json$");
    private static final Pattern RESOURCE_PATH = Pattern.compile(
            ".*model-schema/(protocol|provider|model)/([^/]+)/v[1-9][0-9]*\\.json.*");

    private final ObjectMapper objectMapper;
    private final SafeModelNameMatcher modelNameMatcher = new SafeModelNameMatcher();
    private final CapabilitySchemaDefinitionValidator schemaValidator =
            new CapabilitySchemaDefinitionValidator();

    SchemaProfileResourceLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
    }

    List<SchemaProfileDefinition> load(Resource[] resources) {
        try {
            List<Resource> ordered = List.of(resources).stream()
                    .sorted(Comparator.comparing(Resource::getDescription))
                    .toList();
            Map<SchemaProfileKey, SchemaProfileDefinition> definitions =
                    new LinkedHashMap<>();
            for (Resource resource : ordered) {
                SchemaProfileDefinition definition = read(resource);
                validateDefinition(definition);
                validateResourceIdentity(resource, definition);
                SchemaProfileKey key = new SchemaProfileKey(
                        definition.layer(), definition.code(),
                        definition.profileVersion());
                if (definitions.putIfAbsent(key, definition) != null) {
                    throw new IllegalStateException(
                            "重复 Schema Profile: " + key.layer() + "/"
                                    + key.code() + "@v" + key.version());
                }
            }
            return List.copyOf(definitions.values());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Schema Profile 加载失败", e);
        }
    }

    private SchemaProfileDefinition read(Resource resource) {
        try (InputStream input = resource.getInputStream()) {
            return objectMapper.readValue(input, SchemaProfileDefinition.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Schema Profile 解析失败: " + resource.getDescription(), e);
        }
    }

    private void validateDefinition(SchemaProfileDefinition definition) {
        require(definition != null && definition.layer() != null,
                "Profile layer 不能为空");
        require(matchesCode(definition.code()), "Profile code 格式无效");
        require(definition.profileVersion() > 0,
                "profileVersion 必须是正整数");
        definition.capabilityOverlays().forEach((capability, overlay) -> {
            require(matchesCode(capability), "Profile Capability code 格式无效");
            require(overlay != null && overlay.isObject(),
                    "Profile overlay 必须是 object: " + capability);
        });

        switch (definition.layer()) {
            case PROTOCOL -> {
                require(blank(definition.providerCode())
                                && blank(definition.modelNamePattern()),
                        "Protocol Profile 不允许声明 provider/model 匹配字段");
                require(blank(definition.protocolCode())
                                && blank(definition.defaultBaseUrl())
                                && !definition.modelDiscoverySupported()
                                && definition.supportedCapabilities().isEmpty()
                                && definition.credentialSchema() == null
                                && definition.connectionConfigSchema() == null,
                        "Protocol Profile 不允许声明 Provider 配置字段");
                require(!definition.allowConstraintRelaxation(),
                        "仅 Model Profile 可声明 allowConstraintRelaxation");
                require(!definition.capabilityOverlays().isEmpty(),
                        "Protocol Profile capabilityOverlays 不能为空");
            }
            case PROVIDER -> {
                require(blank(definition.providerCode())
                                && blank(definition.modelNamePattern()),
                        "Provider Profile 不允许声明 provider/model 匹配字段");
                require(!definition.allowConstraintRelaxation(),
                        "仅 Model Profile 可声明 allowConstraintRelaxation");
                require(matchesCode(definition.protocolCode()),
                        "Provider Profile protocolCode 格式无效");
                require(definition.credentialSchema() != null,
                        "Provider Profile credentialSchema 不能为空");
                require(definition.connectionConfigSchema() != null,
                        "Provider Profile connectionConfigSchema 不能为空");
                schemaValidator.validateObjectSchema(
                        definition.credentialSchema(), "credentialSchema");
                schemaValidator.validateObjectSchema(
                        definition.connectionConfigSchema(), "connectionConfigSchema");
                definition.supportedCapabilities().forEach(capability ->
                        require(matchesCode(capability),
                                "Provider supportedCapability 格式无效"));
            }
            case MODEL -> {
                require(matchesCode(definition.providerCode()),
                        "Model Profile providerCode 格式无效");
                modelNameMatcher.validate(definition.modelNamePattern());
                require(blank(definition.protocolCode())
                                && blank(definition.defaultBaseUrl())
                                && !definition.modelDiscoverySupported()
                                && definition.supportedCapabilities().isEmpty()
                                && definition.credentialSchema() == null
                                && definition.connectionConfigSchema() == null,
                        "Model Profile 不允许声明 Provider 配置字段");
                require(!definition.capabilityOverlays().isEmpty(),
                        "Model Profile capabilityOverlays 不能为空");
            }
        }
    }

    private void validateResourceIdentity(
            Resource resource,
            SchemaProfileDefinition definition) {
        String filename = resource.getFilename();
        Matcher filenameMatcher = VERSION_FILE.matcher(filename == null ? "" : filename);
        require(filenameMatcher.matches(),
                "Schema Profile 文件必须命名为 v{version}.json");
        require(Integer.parseInt(filenameMatcher.group(1))
                        == definition.profileVersion(),
                "Schema Profile 文件名与 profileVersion 不一致");

        Matcher pathMatcher = RESOURCE_PATH.matcher(
                resource.getDescription().replace('\\', '/'));
        if (!pathMatcher.matches()) {
            return;
        }
        require(pathMatcher.group(1).equals(
                        definition.layer().name().toLowerCase(Locale.ROOT)),
                "Schema Profile 目录与 layer 不一致");
        require(pathMatcher.group(2).equals(slug(definition.code())),
                "Schema Profile 目录与 code 不一致");
    }

    private boolean matchesCode(String value) {
        return value != null
                && CODE_PATTERN.matcher(value.trim().toUpperCase(Locale.ROOT)).matches();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String slug(String code) {
        return code.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
