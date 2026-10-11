package com.polaris.platform.mcp;

import com.polaris.common.exception.ServiceException;
import com.polaris.platform.dto.McpConnectionProbeResult.ToolPreview;
import io.modelcontextprotocol.json.schema.jackson3.DefaultJsonSchemaValidator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.security.MessageDigest;
import java.util.*;

/** 定义哈希与显式 Schema 支持档位；未知约束不能静默变成任意对象。 */
@Component
public class McpToolContractPolicy {
    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";
    private static final String FASTMCP_WRAP_RESULT = "x-fastmcp-wrap-result";
    private static final Set<String> KEYWORDS = Set.of(
            "$schema", "$ref", "$defs", "definitions", "type", "title", "description", "default", "examples",
            "readOnly", "writeOnly", "deprecated", "enum", "const", "properties", "required", "additionalProperties",
            "minProperties", "maxProperties", "dependentRequired", "dependentSchemas", "propertyNames",
            "items", "prefixItems", "contains", "minContains", "maxContains", "minItems", "maxItems", "uniqueItems",
            "minLength", "maxLength", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
            "allOf", "anyOf", "oneOf", "not", "if", "then", "else", "unevaluatedProperties", "unevaluatedItems");
    private static final Set<String> SCHEMA_MAPS = Set.of("properties", "$defs", "definitions", "dependentSchemas");
    private static final Set<String> SCHEMA_ARRAYS = Set.of("allOf", "anyOf", "oneOf", "prefixItems");
    private static final Set<String> SCHEMA_VALUES = Set.of("additionalProperties", "items", "contains", "propertyNames",
            "not", "if", "then", "else", "unevaluatedProperties", "unevaluatedItems");
    private final DefaultJsonSchemaValidator validator = new DefaultJsonSchemaValidator(JSON);

    public Contract prepare(ToolPreview tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank() || tool.name().length() > 256
                || tool.inputSchema() == null || tool.definition() == null) {
            throw new ServiceException("MCP工具发现返回了无效定义");
        }
        Map<String, Object> definition = new LinkedHashMap<>(tool.definition());
        // SDK 解析的注解、图标、_meta 等全部参与哈希，不仅比较输入参数。
        definition.put("name", tool.name());
        definition.put("title", tool.title());
        definition.put("description", tool.description());
        definition.put("inputSchema", tool.inputSchema());
        definition.put("outputSchema", tool.outputSchema());
        String normalized = JSON.writeValueAsString(sorted(JSON.valueToTree(definition), 0));
        String schemaError = schemaError(tool.inputSchema(), false);
        if (schemaError == null && tool.outputSchema() != null) {
            schemaError = schemaError(tool.outputSchema(), true);
        }
        return new Contract(hash(normalized), JSON.writeValueAsString(tool.inputSchema()),
                tool.outputSchema() == null ? null : JSON.writeValueAsString(tool.outputSchema()),
                normalized, schemaError == null, schemaError);
    }

    /** 输入与结果都按审核时的完整Schema验证；不输出验证器携带的数据原文。 */
    public void validateValue(String schemaJson, Object value, boolean output) {
        try {
            Map<String, Object> schema = JSON.readValue(schemaJson,
                    new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (schemaError(schema, output) != null || !validator.validate(schema, value).valid()) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ignored) {
            throw new McpInvocationException(output ? McpInvocationException.Code.OUTPUT_INVALID
                    : McpInvocationException.Code.INPUT_INVALID);
        } finally {
            // SDK 内置缓存没有容量上限；当前切片不跨调用保留外部Schema。
            validator.clearCache();
        }
    }

    private String schemaError(Map<String, Object> schema, boolean output) {
        try {
            JsonNode root = JSON.valueToTree(schema);
            if (!root.isObject() || !"object".equals(root.path("type").asText())) {
                return "工具Schema根节点须明确声明type=object";
            }
            String error = inspect(root, root, new HashSet<>(), 0, new int[]{0}, output);
            if (error != null) {
                return error;
            }
            return validator.validateSchema(schema).valid() ? null : "Schema未通过2020-12元结构校验";
        } catch (Exception e) {
            return "Schema无法安全解析或校验";
        }
    }

    private String inspect(JsonNode schema, JsonNode root, Set<String> references, int depth, int[] visited,
            boolean output) {
        if (depth > 48 || ++visited[0] > 10000) {
            return "Schema结构过深或过于复杂";
        }
        if (schema.isBoolean()) {
            return null;
        }
        if (!schema.isObject()) {
            return "Schema子结构必须是对象或布尔值";
        }
        for (Map.Entry<String, JsonNode> field : schema.properties()) {
            String key = field.getKey();
            JsonNode value = field.getValue();
            if (FASTMCP_WRAP_RESULT.equals(key) && output && schema == root && value.isBoolean()) {
                // FastMCP 的根输出包装注解：仍校验整个 structuredContent，不自动解包或据此授权。
                // 注解保留在完整定义/哈希中，变化仍须重新发现、审核和绑定。
                continue;
            }
            if (!KEYWORDS.contains(key)) {
                return "Schema包含当前不支持的关键字（含format、pattern、外部或动态引用）";
            }
            if ("$schema".equals(key) && !DIALECT.equals(value.asText())) {
                return "当前仅支持JSON Schema 2020-12或未声明方言";
            }
            if ("$ref".equals(key)) {
                String reference = value.asText();
                if (!value.isString() || !reference.startsWith("#/")) {
                    return "仅支持当前文档内的JSON Pointer引用";
                }
                JsonNode target;
                try { target = root.at(reference.substring(1)); }
                catch (IllegalArgumentException e) { return "Schema本地引用无效"; }
                if (target.isMissingNode() || !references.add(reference)) {
                    return "Schema包含缺失或递归引用";
                }
                String error = inspect(target, root, references, depth + 1, visited, output);
                references.remove(reference);
                if (error != null) return error;
            } else if (SCHEMA_MAPS.contains(key)) {
                if (!value.isObject()) return "Schema子定义集合格式无效";
                for (JsonNode child : value) {
                    String error = inspect(child, root, references, depth + 1, visited, output);
                    if (error != null) return error;
                }
            } else if (SCHEMA_ARRAYS.contains(key)) {
                if (!value.isArray()) return "Schema组合约束格式无效";
                for (JsonNode child : value) {
                    String error = inspect(child, root, references, depth + 1, visited, output);
                    if (error != null) return error;
                }
            } else if (SCHEMA_VALUES.contains(key)) {
                String error = inspect(value, root, references, depth + 1, visited, output);
                if (error != null) return error;
            }
        }
        return null;
    }

    private JsonNode sorted(JsonNode value, int depth) {
        if (depth > 64) {
            throw new ServiceException("MCP工具定义层级超过安全上限");
        }
        if (value.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            Set<String> names = new TreeSet<>();
            value.properties().forEach(field -> names.add(field.getKey()));
            names.forEach(name -> result.set(name, sorted(value.get(name), depth + 1)));
            return result;
        }
        if (value.isArray()) {
            var result = JsonNodeFactory.instance.arrayNode();
            value.forEach(child -> result.add(sorted(child, depth + 1)));
            return result;
        }
        return value;
    }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("MCP结构哈希计算失败");
        }
    }

    public record Contract(String structureHash, String inputSchema, String outputSchema,
            String definitionJson, boolean supported, String schemaError) {
    }
}
