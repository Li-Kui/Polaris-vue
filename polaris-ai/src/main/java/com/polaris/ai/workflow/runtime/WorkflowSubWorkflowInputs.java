package com.polaris.ai.workflow.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.workflow.compiler.WorkflowExpressionParser;

/** 可视化递归输入：对象、数组和整体引用都保持 JSON 原始类型。 */
public final class WorkflowSubWorkflowInputs {
    private WorkflowSubWorkflowInputs() {}

    public static JsonNode compile(JsonNode tree) { return compile(tree, 0, new int[]{0}); }
    private static JsonNode compile(JsonNode tree, int depth, int[] count) {
        if (depth > 20 || ++count[0] > 2000) throw new IllegalArgumentException("子工作流输入结构过大");
        if (tree == null || !tree.isObject()) throw new IllegalArgumentException("请配置子工作流输入");
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        String mode = tree.path("mode").asText("DEFAULT"); result.put("mode", mode);
        switch (mode) {
            case "SOURCE" -> result.set("ast", new WorkflowExpressionParser().parse(tree.path("expression").asText()));
            case "VALUE" -> {
                if (!tree.has("value")) throw new IllegalArgumentException("固定值尚未填写");
                result.set("value", tree.get("value"));
            }
            case "JSON" -> {
                String json = tree.path("json").asText();
                if (json.isBlank() || json.length() > 1024 * 1024) throw new IllegalArgumentException("固定 JSON 不能为空或超过 1MB");
                try {
                    JsonNode value = new com.fasterxml.jackson.databind.ObjectMapper()
                            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(json);
                    result.put("mode", "VALUE"); result.set("value", value);
                } catch (java.io.IOException e) { throw new IllegalArgumentException("固定 JSON 格式不正确"); }
            }
            case "OBJECT" -> {
                if (!tree.path("fields").isObject()) throw new IllegalArgumentException("对象输入的字段配置无效");
                var fields = result.putObject("fields");
                tree.path("fields").fields().forEachRemaining(e -> {
                    if (java.util.Set.of("__proto__", "constructor", "prototype").contains(e.getKey()))
                        throw new IllegalArgumentException("不支持的字段名称");
                    fields.set(e.getKey(), compile(e.getValue(), depth + 1, count));
                });
            }
            case "ARRAY" -> {
                if (!tree.path("items").isArray()) throw new IllegalArgumentException("数组输入的元素配置无效");
                var items = result.putArray("items");
                tree.path("items").forEach(item -> items.add(compile(item, depth + 1, count)));
            }
            case "DEFAULT" -> { }
            default -> throw new IllegalArgumentException("不支持的输入方式");
        }
        return result;
    }

    /** 已知常量与缺少的必填项在发布前报错；动态来源仍在实际调用前校验。 */
    public static void validate(JsonNode plan, JsonNode schema) {
        validate(plan, schema, "$", true);
    }

    private static void validate(JsonNode plan, JsonNode schema, String path, boolean required) {
        if (schema == null) schema = JsonNodeFactory.instance.objectNode();
        String mode = plan.path("mode").asText("DEFAULT");
        if ("DEFAULT".equals(mode)) {
            if (required && !schema.has("default")) throw new IllegalArgumentException(path + "：请填写或选择来源");
            if (schema.has("default")) checkValue(schema, schema.get("default"), path);
            return;
        }
        if ("SOURCE".equals(mode)) return;
        if ("VALUE".equals(mode)) { checkValue(schema, plan.get("value"), path); return; }
        var empty = JsonNodeFactory.instance.objectNode();
        if ("OBJECT".equals(mode)) {
            // 只校验容器类型；required 在各字段递归检查，以允许动态引用。
            var container = schema.deepCopy();
            if (container.isObject()) ((ObjectNode) container).remove("required");
            checkValue(container, empty, path);
            final JsonNode objectSchema = schema;
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            schema.path("properties").fieldNames().forEachRemaining(names::add);
            plan.path("fields").fieldNames().forEachRemaining(names::add);
            schema.path("required").forEach(name -> names.add(name.asText()));
            for (String name : names) {
                JsonNode field = plan.path("fields").path(name);
                if (!schema.path("properties").has(name) && schema.path("additionalProperties").isBoolean()
                        && !schema.path("additionalProperties").asBoolean() && !"DEFAULT".equals(field.path("mode").asText("DEFAULT")))
                    throw new IllegalArgumentException(path + "." + name + "：不允许的字段");
                boolean childRequired = false;
                for (JsonNode key : objectSchema.path("required")) if (name.equals(key.asText())) childRequired = true;
                validate(field, schema.path("properties").path(name), path + "." + name, childRequired);
            }
        } else if ("ARRAY".equals(mode)) {
            var container = schema.deepCopy();
            if (container.isObject()) ((ObjectNode) container).remove(java.util.List.of("items", "uniqueItems"));
            var placeholders = JsonNodeFactory.instance.arrayNode();
            plan.path("items").forEach(item -> placeholders.addNull());
            checkValue(container, placeholders, path);
            for (int i = 0; i < plan.path("items").size(); i++) validate(plan.path("items").get(i), schema.path("items"), path + "[" + i + "]", true);
        }
    }

    private static void checkValue(JsonNode schema, JsonNode value, String path) {
        var errors = new WorkflowInputValidator().validate(schema, value, path);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("；", errors));
    }

    public static JsonNode evaluate(JsonNode plan, JsonNode schema, JsonNode context, WorkflowExpressionEvaluator evaluator) {
        String mode = plan.path("mode").asText("DEFAULT");
        return switch (mode) {
            case "SOURCE" -> evaluator.evaluate(plan.get("ast"), context).deepCopy();
            case "VALUE" -> plan.path("value").deepCopy();
            case "OBJECT" -> {
                ObjectNode value = JsonNodeFactory.instance.objectNode();
                plan.path("fields").fields().forEachRemaining(e -> {
                    JsonNode child = evaluate(e.getValue(), schema.path("properties").path(e.getKey()), context, evaluator);
                    if (!child.isMissingNode()) value.set(e.getKey(), child);
                });
                // 显式未填写的字段才使用契约默认值；null、0、false、空字符串均不回退。
                schema.path("properties").fields().forEachRemaining(e -> {
                    if (!value.has(e.getKey()) && e.getValue().has("default")) value.set(e.getKey(), e.getValue().get("default").deepCopy());
                });
                yield value;
            }
            case "ARRAY" -> {
                ArrayNode value = JsonNodeFactory.instance.arrayNode();
                for (JsonNode item : plan.path("items")) {
                    JsonNode child = evaluate(item, schema.path("items"), context, evaluator);
                    if (child.isMissingNode()) throw new IllegalArgumentException("数组元素尚未填写");
                    value.add(child);
                }
                yield value;
            }
            default -> schema.has("default") ? schema.get("default").deepCopy() : MissingNode.getInstance();
        };
    }
}
