package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/** 使用 Provider Profile 的受控 Schema 校验凭据和非敏感连接配置。 */
@Component
public class ProviderProfileValueValidator {

    private static final int MAX_JSON_LENGTH = 16_384;
    private static final int MAX_DEPTH = 5;
    private static final int MAX_NODES = 128;
    private static final Set<String> SECRET_CONFIG_TOKENS = Set.of(
            "apikey", "secretkey", "token", "password", "authorization",
            "credential", "header", "accesskey", "secretaccesskey");

    private final SchemaProfileRegistry profileRegistry;
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    public ProviderProfileValueValidator(SchemaProfileRegistry profileRegistry) {
        this.profileRegistry = profileRegistry;
    }

    public SchemaProfileDefinition requireProvider(
            String providerCode,
            String protocolCode) {
        SchemaProfileDefinition profile = profileRegistry.findLatest(
                        SchemaProfileDefinition.ProfileLayer.PROVIDER, providerCode)
                .orElseThrow(() -> new ServiceException(
                        "Provider Profile 不存在: " + normalize(providerCode)));
        if (!normalize(profile.protocolCode()).equals(normalize(protocolCode))) {
            throw new ServiceException(
                    "Provider 与 Protocol 不匹配: " + normalize(providerCode));
        }
        return profile;
    }

    public Map<String, Object> validateCredential(
            SchemaProfileDefinition profile,
            Map<String, Object> credential,
            boolean required) {
        Map<String, Object> value = copy(credential);
        if (!required && value.isEmpty()) {
            return Map.of();
        }
        validate(profile.credentialSchema(), value, "Credential");
        return value;
    }

    public Map<String, Object> validateExtraConfig(
            SchemaProfileDefinition profile,
            Map<String, Object> extraConfig) {
        Map<String, Object> value = copy(extraConfig);
        rejectSecretKeys(value, "$", 0);
        validate(profile.connectionConfigSchema(), value, "extraConfig");
        return value;
    }

    public String serializeExtraConfig(Map<String, Object> extraConfig) {
        if (extraConfig == null || extraConfig.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(extraConfig);
            if (json.length() > MAX_JSON_LENGTH) {
                throw new ServiceException("extraConfig 内容不能超过 16384 字符");
            }
            return json;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("extraConfig 序列化失败");
        }
    }

    private void validate(
            SchemaNode schema,
            Map<String, Object> value,
            String label) {
        JsonNode node = objectMapper.valueToTree(value);
        if (node.toString().length() > MAX_JSON_LENGTH) {
            throw new ServiceException(label + " 内容不能超过 16384 字符");
        }
        List<String> errors = new ArrayList<>();
        int[] nodes = {0};
        validateNode(schema, node, "$", 0, nodes, errors);
        if (!errors.isEmpty()) {
            throw new ServiceException(
                    label + " 校验失败：" + String.join("; ", errors));
        }
    }

    private void validateNode(
            SchemaNode schema,
            JsonNode value,
            String path,
            int depth,
            int[] nodes,
            List<String> errors) {
        if (depth > MAX_DEPTH || ++nodes[0] > MAX_NODES) {
            errors.add(path + " 结构过于复杂");
            return;
        }
        if (value == null || value.isNull()) {
            errors.add(path + " 不允许为 null");
            return;
        }
        boolean matches = switch (schema.type()) {
            case OBJECT -> value.isObject();
            case STRING -> value.isTextual();
            case INTEGER -> value.isIntegralNumber();
            case NUMBER -> value.isNumber();
            case BOOLEAN -> value.isBoolean();
            case ARRAY -> value.isArray();
        };
        if (!matches) {
            errors.add(path + " 类型必须是 " + schema.type().toJson());
            return;
        }
        if (!schema.enumValues().isEmpty()
                && schema.enumValues().stream().noneMatch(value::equals)) {
            errors.add(path + " 不在允许的 enum 中");
        }
        switch (schema.type()) {
            case OBJECT -> validateObject(
                    schema, value, path, depth, nodes, errors);
            case STRING -> validateString(schema, value, path, errors);
            case INTEGER, NUMBER -> validateNumber(schema, value, path, errors);
            case ARRAY -> {
                for (int index = 0; index < value.size(); index++) {
                    validateNode(schema.items(), value.get(index),
                            path + "[" + index + "]", depth + 1, nodes, errors);
                }
            }
            case BOOLEAN -> {
                // 类型检查已覆盖 boolean 全部约束。
            }
        }
    }

    private void validateObject(
            SchemaNode schema,
            JsonNode value,
            String path,
            int depth,
            int[] nodes,
            List<String> errors) {
        schema.required().forEach(field -> {
            if (!value.has(field) || value.get(field).isNull()) {
                errors.add(path + "." + field + " 为必填字段");
            }
        });
        value.properties().forEach(entry -> {
            SchemaNode child = schema.properties().get(entry.getKey());
            if (child == null) {
                errors.add(path + "." + entry.getKey() + " 是未允许的字段");
                return;
            }
            validateNode(child, entry.getValue(), path + "." + entry.getKey(),
                    depth + 1, nodes, errors);
        });
    }

    private void validateString(
            SchemaNode schema,
            JsonNode value,
            String path,
            List<String> errors) {
        int length = value.textValue().codePointCount(0, value.textValue().length());
        if (schema.minLength() != null && length < schema.minLength()) {
            errors.add(path + " 长度不能小于 " + schema.minLength());
        }
        if (schema.maxLength() != null && length > schema.maxLength()) {
            errors.add(path + " 长度不能大于 " + schema.maxLength());
        }
    }

    private void validateNumber(
            SchemaNode schema,
            JsonNode value,
            String path,
            List<String> errors) {
        BigDecimal number = value.decimalValue();
        if (schema.minimum() != null && number.compareTo(schema.minimum()) < 0) {
            errors.add(path + " 不能小于 " + schema.minimum());
        }
        if (schema.maximum() != null && number.compareTo(schema.maximum()) > 0) {
            errors.add(path + " 不能大于 " + schema.maximum());
        }
    }

    private void rejectSecretKeys(
            Map<String, Object> value,
            String path,
            int depth) {
        if (depth > MAX_DEPTH) {
            throw new ServiceException("extraConfig 嵌套过深");
        }
        value.forEach((key, child) -> {
            String normalized = key.replaceAll("[^A-Za-z0-9]", "")
                    .toLowerCase(Locale.ROOT);
            if (SECRET_CONFIG_TOKENS.stream().anyMatch(normalized::contains)) {
                throw new ServiceException(
                        "extraConfig 禁止包含敏感或请求头字段: "
                                + path + "." + key);
            }
            if (child instanceof Map<?, ?> nested) {
                Map<String, Object> converted = new LinkedHashMap<>();
                nested.forEach((nestedKey, nestedValue) ->
                        converted.put(String.valueOf(nestedKey), nestedValue));
                rejectSecretKeys(converted, path + "." + key, depth + 1);
            }
        });
    }

    private Map<String, Object> copy(Map<String, Object> value) {
        return value == null || value.isEmpty()
                ? Map.of() : Map.copyOf(new LinkedHashMap<>(value));
    }

    private String normalize(String value) {
        return value == null ? "null" : value.trim().toUpperCase(Locale.ROOT);
    }
}
