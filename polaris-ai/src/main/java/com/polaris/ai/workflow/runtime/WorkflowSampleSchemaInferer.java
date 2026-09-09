package com.polaris.ai.workflow.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/** 从脱敏 JSON 样本保守推导仅供编辑器展示的 JSON Schema。 */
public class WorkflowSampleSchemaInferer {

    private static final int MAX_DEPTH = 12;
    private static final int MAX_PROPERTIES = 200;
    private static final int MAX_ARRAY_SAMPLES = 50;

    private final ObjectMapper objectMapper;

    public WorkflowSampleSchemaInferer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Result infer(JsonNode sample) {
        return infer(List.of(sample == null ? NullNode.getInstance() : sample));
    }

    public Result infer(List<JsonNode> samples) {
        List<JsonNode> values = samples == null ? List.of() : samples.stream()
                .filter(Objects::nonNull).toList();
        List<String> diagnostics = new ArrayList<>();
        if (values.isEmpty()) {
            diagnostics.add("没有可用于推导的试运行样本");
            return new Result(objectMapper.createObjectNode(), List.copyOf(diagnostics));
        }
        boolean allowRequired = values.size() >= 2;
        diagnostics.add(allowRequired
                ? "已聚合 " + values.size() + " 次兼容试运行样本；仅所有样本均出现的字段建议为 required"
                : "当前仅使用单次试运行样本，所有字段均按可选处理，不推断 required");
        int[] propertyCount = {0};
        JsonNode schema = inferValues(
                values, "$", 1, propertyCount, diagnostics, allowRequired);
        return new Result(schema, List.copyOf(diagnostics));
    }

    private JsonNode inferValues(
            List<JsonNode> values, String path, int depth, int[] propertyCount,
            List<String> diagnostics, boolean allowRequired) {
        if (depth > MAX_DEPTH) {
            diagnostics.add(path + " 超过样本推导最大深度，保留为开放结构");
            return objectMapper.createObjectNode();
        }
        ObjectNode schema = objectMapper.createObjectNode();
        Set<String> types = new LinkedHashSet<>();
        values.forEach(value -> types.add(typeOf(value)));
        if (types.contains("number")) types.remove("integer");
        writeTypes(schema, types);

        List<JsonNode> objects = values.stream().filter(JsonNode::isObject).toList();
        if (!objects.isEmpty()) {
            inferObject(schema, objects, path, depth, propertyCount,
                    diagnostics, allowRequired);
        }
        List<JsonNode> arrays = values.stream().filter(JsonNode::isArray).toList();
        if (!arrays.isEmpty()) {
            inferArray(schema, arrays, path, depth, propertyCount,
                    diagnostics, allowRequired);
        }
        long nonNullTypeCount = types.stream().filter(type -> !"null".equals(type)).count();
        if (nonNullTypeCount > 1) {
            diagnostics.add(path + " 在不同样本中出现类型冲突，已保留联合类型");
        }
        return schema;
    }

    private void inferObject(
            ObjectNode schema, List<JsonNode> objects, String path, int depth,
            int[] propertyCount, List<String> diagnostics, boolean allowRequired) {
        ObjectNode properties = schema.putObject("properties");
        Set<String> names = new LinkedHashSet<>();
        objects.forEach(value -> value.fieldNames().forEachRemaining(names::add));
        ArrayNode required = objectMapper.createArrayNode();
        boolean truncated = false;
        for (String name : names) {
            if (propertyCount[0] >= MAX_PROPERTIES) {
                truncated = true;
                break;
            }
            propertyCount[0]++;
            List<JsonNode> present = objects.stream()
                    .filter(value -> value.has(name))
                    .map(value -> value.get(name))
                    .toList();
            properties.set(name, inferValues(
                    present, path + "." + name, depth + 1,
                    propertyCount, diagnostics, allowRequired));
            if (allowRequired && objects.size() >= 2 && present.size() == objects.size()) {
                required.add(name);
            } else if (allowRequired && objects.size() >= 2) {
                diagnostics.add(path + "." + name + " 仅在 " + present.size()
                        + "/" + objects.size() + " 个对象样本中出现，保持可选");
            }
        }
        if (truncated) {
            diagnostics.add("样本字段超过 " + MAX_PROPERTIES + " 个，已截断推导结果");
        }
        if (!required.isEmpty()) schema.set("required", required);
        schema.put("additionalProperties", true);
    }

    private void inferArray(
            ObjectNode schema, List<JsonNode> arrays, String path, int depth,
            int[] propertyCount, List<String> diagnostics, boolean allowRequired) {
        List<JsonNode> elements = new ArrayList<>();
        int available = 0;
        for (JsonNode array : arrays) {
            available += array.size();
            for (JsonNode element : array) {
                if (elements.size() >= MAX_ARRAY_SAMPLES) break;
                elements.add(element);
            }
        }
        if (elements.isEmpty()) {
            schema.set("items", objectMapper.createObjectNode());
            diagnostics.add(path + " 在全部样本中均为空数组，暂时无法确定元素类型");
            return;
        }
        schema.set("items", inferValues(
                elements, path + "[]", depth + 1,
                propertyCount, diagnostics, allowRequired));
        if (available > elements.size()) {
            diagnostics.add(path + " 共发现 " + available + " 个数组元素，仅采样前 "
                    + elements.size() + " 个");
        }
    }

    private String typeOf(JsonNode value) {
        if (value == null || value.isNull()) return "null";
        if (value.isObject()) return "object";
        if (value.isArray()) return "array";
        if (value.isIntegralNumber()) return "integer";
        if (value.isFloatingPointNumber()) return "number";
        if (value.isBoolean()) return "boolean";
        return "string";
    }

    private void writeTypes(ObjectNode schema, Set<String> types) {
        if (types.size() == 1) {
            schema.put("type", types.iterator().next());
            return;
        }
        ArrayNode values = schema.putArray("type");
        types.forEach(values::add);
    }

    public record Result(JsonNode schema, List<String> diagnostics) {
    }
}
