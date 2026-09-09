package com.polaris.ai.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.polaris.ai.workflow.spi.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 确定性内置节点配置；依赖连接器的节点单独注册。 */
@Configuration
public class BuiltInWorkflowNodeConfig {

    private static final String TRANSFORM_TARGET_PATH_PATTERN =
            "^(?:\\$|[A-Za-z_\\u0080-\\uFFFF][A-Za-z0-9_\\-\\u0080-\\uFFFF]*(?:\\[\\])*(?:\\.[A-Za-z_\\u0080-\\uFFFF][A-Za-z0-9_\\-\\u0080-\\uFFFF]*(?:\\[\\])*){0,9})$";
    private static final String TRANSFORM_SOURCE_PATH_PATTERN =
            "^$|^(?:[A-Za-z_\\u0080-\\uFFFF][A-Za-z0-9_\\-\\u0080-\\uFFFF]*|\\[\\])(?:\\[\\])*(?:\\.(?:[A-Za-z_\\u0080-\\uFFFF][A-Za-z0-9_\\-\\u0080-\\uFFFF]*|[0-9]+)(?:\\[\\])*)*$";
    private static final String TRANSFORM_SIMPLE_PATH_PATTERN =
            "^$|^[A-Za-z_\\u0080-\\uFFFF][A-Za-z0-9_\\-\\u0080-\\uFFFF]*(?:\\.(?:[A-Za-z_\\u0080-\\uFFFF][A-Za-z0-9_\\-\\u0080-\\uFFFF]*|[0-9]+))*$";
    private static final Pattern TRANSFORM_TEMPLATE_PATTERN = Pattern.compile(
            "\\$\\{([\\p{L}_][\\p{L}\\p{N}_-]*(?:\\.[\\p{L}\\p{N}_-]+)*)}");

    @Bean
    public WorkflowNodeHandler transformWorkflowNodeHandler() {
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "transform", "1.0", "数据转换", "data",
                transformConfigSchema(), openObjectSchema(), JsonNodeFactory.instance.objectNode(),
                WorkflowSideEffect.NONE, Set.of(),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.RETRYABLE,
                        WorkflowNodeCapability.MOCKABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return new WorkflowNodeHandler() {
            @Override
            public WorkflowNodeDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public WorkflowNodeResult execute(WorkflowNodeContext context) {
                context.cancellation().throwIfCancellationRequested();
                JsonNode input = context.input() == null ? NullNode.instance : context.input();
                JsonNode source = input.isObject() && input.has("source")
                        ? input.get("source") : input;
                ObjectNode sources = JsonNodeFactory.instance.objectNode();
                if (input.isObject() && input.has("source")) {
                    input.properties().forEach(entry ->
                            sources.set(entry.getKey(), entry.getValue().deepCopy()));
                } else {
                    sources.set("source", source.deepCopy());
                }
                JsonNode config = context.config() == null
                        ? JsonNodeFactory.instance.objectNode() : context.config();
                if (!config.has("mode") && !config.has("rules")) {
                    return WorkflowNodeResult.success(source.deepCopy());
                }
                String mode = config.path("mode").asText("OBJECT_MAP");
                JsonNode rules = config.path("rules");
                validateTransformRules(rules, mode);
                boolean preserveUnmapped = config.path("preserveUnmapped").asBoolean(false);
                int maximum = config.path("maxItems").asInt(1000);
                String arrayAlignment = config.path("arrayAlignment").asText("PRIMARY");
                String alignmentPath = config.path("alignmentPath").asText("");
                if ("VALUE".equals(mode)) {
                    return WorkflowNodeResult.success(transformRootValue(
                            source, sources, rules, maximum));
                }
                if ("ARRAY_MAP".equals(mode)) {
                    validateArrayAlignment(arrayAlignment, alignmentPath);
                    if (!source.isArray()) {
                        throw new IllegalArgumentException("数组逐项整理要求数据来源是数组");
                    }
                    int outputSize = alignedArrayLength(
                            sources, source, arrayAlignment, maximum);
                    ArrayNode output = JsonNodeFactory.instance.arrayNode();
                    for (int index = 0; index < outputSize; index++) {
                        context.cancellation().throwIfCancellationRequested();
                        JsonNode primaryItem = index < source.size()
                                ? source.get(index) : MissingNode.getInstance();
                        output.add(transformObject(
                                primaryItem, sources, rules, preserveUnmapped,
                                index, maximum, arrayAlignment, alignmentPath));
                    }
                    return WorkflowNodeResult.success(output);
                }
                if (!"OBJECT_MAP".equals(mode)) {
                    throw new IllegalArgumentException("不支持的数据转换模式: " + mode);
                }
                return WorkflowNodeResult.success(
                        transformObject(source, sources, rules,
                                preserveUnmapped, null, maximum,
                                "PRIMARY", ""));
            }
        };
    }

    @Bean
    public WorkflowNodeSchemaResolver transformWorkflowNodeSchemaResolver() {
        return new WorkflowNodeSchemaResolver() {
            @Override
            public boolean supports(String nodeType, String handlerVersion) {
                return "transform".equals(nodeType) && "1.0".equals(handlerVersion);
            }

            @Override
            public ResolvedNodeSchema resolve(WorkflowNodeSchemaContext context) {
                List<String> diagnostics = new ArrayList<>();
                Map<String, String> fieldSources = new LinkedHashMap<>();
                JsonNode config = context.config() == null
                        ? JsonNodeFactory.instance.objectNode() : context.config();
                if (!config.has("mode") && !config.has("rules")) {
                    return new ResolvedNodeSchema(
                            context.declaredInputSchema(), context.declaredOutputSchema(),
                            "NODE_CONTRACT", context.handlerVersion(), Map.of(), List.of());
                }
                if ("VALUE".equals(config.path("mode").asText())) {
                    JsonNode rules = config.path("rules");
                    ObjectNode outputSchema = JsonNodeFactory.instance.objectNode();
                    try {
                        validateTransformRules(rules, "VALUE");
                        outputSchema = operationResultSchema(rules.get(0));
                        fieldSources.put("$", "TRANSFORM_RULE");
                    } catch (IllegalArgumentException exception) {
                        diagnostics.add(exception.getMessage());
                    }
                    return new ResolvedNodeSchema(
                            context.declaredInputSchema(), outputSchema,
                            "TRANSFORM_RULES", transformFingerprint(config),
                            fieldSources, diagnostics);
                }
                ObjectNode itemSchema = transformOutputItemSchema(
                        config, diagnostics, fieldSources);
                JsonNode outputSchema;
                if ("ARRAY_MAP".equals(config.path("mode").asText())) {
                    ObjectNode arraySchema = JsonNodeFactory.instance.objectNode();
                    arraySchema.put("type", "array");
                    arraySchema.set("items", itemSchema);
                    outputSchema = arraySchema;
                } else {
                    outputSchema = itemSchema;
                }
                return new ResolvedNodeSchema(
                        context.declaredInputSchema(), outputSchema,
                        "TRANSFORM_RULES", transformFingerprint(config),
                        fieldSources, diagnostics);
            }
        };
    }

    private ObjectNode transformConfigSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("version").put("type", "integer")
                .put("minimum", 1).put("maximum", 1).put("default", 1);
        properties.putObject("mode").put("type", "string")
                .put("title", "整理方式").put("default", "OBJECT_MAP")
                .putArray("enum").add("OBJECT_MAP").add("ARRAY_MAP").add("VALUE");
        properties.putObject("preserveUnmapped").put("type", "boolean")
                .put("title", "保留未配置字段").put("default", false);
        properties.putObject("maxItems").put("type", "integer")
                .put("title", "数组最大处理条数")
                .put("minimum", 1).put("maximum", 10000).put("default", 1000);
        properties.putObject("arrayAlignment").put("type", "string")
                .put("title", "多个数组的对齐方式").put("default", "PRIMARY")
                .putArray("enum").add("PRIMARY").add("STRICT").add("SHORTEST")
                .add("LONGEST").add("KEYED");
        properties.putObject("alignmentPath").put("type", "string")
                .put("title", "数组关联字段")
                .put("pattern", TRANSFORM_SIMPLE_PATH_PATTERN);

        ObjectNode rules = properties.putObject("rules");
        rules.put("type", "array");
        rules.put("maxItems", 200);
        rules.set("default", JsonNodeFactory.instance.arrayNode());
        ObjectNode rule = rules.putObject("items");
        rule.put("type", "object");
        rule.putArray("required").add("targetPath").add("operation");
        rule.put("additionalProperties", false);
        ObjectNode ruleProperties = rule.putObject("properties");
        ruleProperties.putObject("targetPath").put("type", "string")
                .put("title", "输出字段")
                .put("pattern", TRANSFORM_TARGET_PATH_PATTERN);
        ruleProperties.putObject("sourcePath").put("type", "string")
                .put("title", "来源字段")
                .put("pattern", TRANSFORM_SOURCE_PATH_PATTERN);
        ruleProperties.putObject("operation").put("type", "string")
                .put("title", "处理方式").putArray("enum")
                .add("COPY").add("CONSTANT")
                .add("TO_STRING").add("TO_INTEGER").add("TO_NUMBER").add("TO_BOOLEAN")
                .add("TRIM").add("UPPERCASE").add("LOWERCASE")
                .add("ARRAY_JOIN").add("ARRAY_LENGTH")
                .add("CONCAT").add("DATE_FORMAT")
                .add("ARRAY_FILTER").add("ARRAY_FLATTEN").add("ARRAY_SORT")
                .add("ARRAY_DISTINCT").add("ARRAY_GROUP").add("ARRAY_AGGREGATE")
                .add("TEMPLATE").add("EXPRESSION");
        ruleProperties.putObject("sourceKey").put("type", "string")
                .put("pattern", "^[A-Za-z_][A-Za-z0-9_]{0,39}$")
                .put("default", "source");
        ruleProperties.putObject("resultType").put("type", "string")
                .put("title", "输出类型").putArray("enum")
                .add("object").add("array").add("string").add("integer")
                .add("number").add("boolean").add("null");
        ruleProperties.putObject("sourceType").put("type", "string")
                .putArray("enum").add("object").add("array").add("string")
                .add("integer").add("number").add("boolean").add("null");
        ruleProperties.putObject("resultSchema").put("type", "object")
                .put("title", "来源字段结构");
        ruleProperties.set("value", JsonNodeFactory.instance.objectNode());
        ruleProperties.putObject("defaultWhen").put("type", "string")
                .put("default", "NEVER").putArray("enum")
                .add("NEVER").add("MISSING").add("NULL")
                .add("BLANK").add("NULL_OR_BLANK");
        ruleProperties.set("defaultValue", JsonNodeFactory.instance.objectNode());
        ruleProperties.putObject("onError").put("type", "string")
                .put("default", "FAIL").putArray("enum")
                .add("FAIL").add("NULL").add("DEFAULT").add("KEEP");
        ruleProperties.putObject("required").put("type", "boolean").put("default", false);
        ruleProperties.putObject("separator").put("type", "string")
                .put("maxLength", 128).put("default", ",");
        ruleProperties.putObject("prefix").put("type", "string").put("maxLength", 2000);
        ruleProperties.putObject("suffix").put("type", "string").put("maxLength", 2000);
        ObjectNode sourcePaths = ruleProperties.putObject("sourcePaths");
        sourcePaths.put("type", "array").put("maxItems", 50);
        sourcePaths.putObject("items").put("type", "string")
                .put("pattern", TRANSFORM_SOURCE_PATH_PATTERN);
        ruleProperties.putObject("inputFormat").put("type", "string").put("maxLength", 128);
        ruleProperties.putObject("outputFormat").put("type", "string")
                .put("maxLength", 128).put("default", "yyyy-MM-dd HH:mm:ss");
        ruleProperties.putObject("timezone").put("type", "string")
                .put("maxLength", 80).put("default", "Asia/Shanghai");
        ruleProperties.putObject("filterPath").put("type", "string")
                .put("pattern", TRANSFORM_SOURCE_PATH_PATTERN);
        ruleProperties.putObject("filterOperator").put("type", "string")
                .putArray("enum").add("EQ").add("NE").add("GT").add("GTE")
                .add("LT").add("LTE").add("CONTAINS").add("STARTS_WITH")
                .add("ENDS_WITH").add("IS_NULL").add("NOT_NULL");
        ruleProperties.set("filterValue", JsonNodeFactory.instance.objectNode());
        ruleProperties.putObject("depth").put("type", "integer")
                .put("minimum", 1).put("maximum", 10).put("default", 1);
        ruleProperties.putObject("sortPath").put("type", "string")
                .put("pattern", TRANSFORM_SOURCE_PATH_PATTERN);
        ruleProperties.putObject("sortDirection").put("type", "string")
                .putArray("enum").add("ASC").add("DESC");
        ruleProperties.putObject("nulls").put("type", "string")
                .putArray("enum").add("FIRST").add("LAST");
        ruleProperties.putObject("distinctPath").put("type", "string")
                .put("pattern", TRANSFORM_SOURCE_PATH_PATTERN);
        ruleProperties.putObject("groupPath").put("type", "string")
                .put("pattern", TRANSFORM_SOURCE_PATH_PATTERN);
        ruleProperties.putObject("groupAggregate").put("type", "string")
                .putArray("enum").add("NONE").add("COUNT").add("SUM")
                .add("AVG").add("MIN").add("MAX");
        ruleProperties.putObject("groupAggregatePath").put("type", "string")
                .put("pattern", TRANSFORM_SOURCE_PATH_PATTERN);
        ruleProperties.putObject("aggregatePath").put("type", "string")
                .put("pattern", TRANSFORM_SOURCE_PATH_PATTERN);
        ruleProperties.putObject("aggregate").put("type", "string")
                .putArray("enum").add("COUNT").add("SUM").add("AVG").add("MIN").add("MAX");
        ruleProperties.putObject("template").put("type", "string").put("maxLength", 12000);
        ruleProperties.putObject("expression").put("type", "string").put("maxLength", 2000);
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode openObjectSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", true);
        return schema;
    }

    private JsonNode transformObject(
            JsonNode source, ObjectNode sources, JsonNode rules, boolean preserveUnmapped,
            Integer arrayIndex, int maximumArrayItems,
            String arrayAlignment, String alignmentPath) {
        ObjectNode contextualSources = transformSourcesForIndex(
                sources, source, arrayIndex, arrayAlignment, alignmentPath);
        ObjectNode output = preserveUnmapped && source != null && source.isObject()
                ? ((ObjectNode) source).deepCopy()
                : JsonNodeFactory.instance.objectNode();
        if (rules == null || !rules.isArray()) {
            return output;
        }
        for (JsonNode rule : rules) {
            String targetPath = rule.path("targetPath").asText("").trim();
            validateTargetPath(targetPath);
            String operation = rule.path("operation").asText("COPY");
            List<TransformPathToken> targetTokens = parseTransformPath(targetPath);
            if ("CONSTANT".equals(operation)) {
                writeTransformLeaf(output, targetTokens, List.of(), rule.path("value"));
                continue;
            }
            JsonNode ruleSource = transformRuleSource(source, contextualSources, rule);
            List<TransformPathToken> sourceTokens = parseTransformPath(
                    rule.path("sourcePath").asText(""));
            walkTransformSource(ruleSource, sourceTokens, 0, new ArrayList<>(), 0,
                    output, targetTokens, rule, arrayIndex, maximumArrayItems,
                    ruleSource, contextualSources);
        }
        return output;
    }

    private ObjectNode transformSourcesForIndex(
            ObjectNode sources, JsonNode primarySource, Integer arrayIndex,
            String arrayAlignment, String alignmentPath) {
        ObjectNode contextual = JsonNodeFactory.instance.objectNode();
        JsonNode primaryKey = "KEYED".equals(arrayAlignment)
                ? readTransformPath(primarySource, alignmentPath)
                : MissingNode.getInstance();
        sources.properties().forEach(entry -> {
            JsonNode candidate = entry.getValue();
            if (arrayIndex != null && candidate.isArray()) {
                candidate = "KEYED".equals(arrayAlignment) && !"source".equals(entry.getKey())
                        ? keyedArrayItem(candidate, primaryKey, alignmentPath, entry.getKey())
                        : arrayIndex < candidate.size()
                                ? candidate.path(arrayIndex) : MissingNode.getInstance();
            }
            contextual.set(entry.getKey(), candidate.deepCopy());
        });
        contextual.set("source", primarySource == null
                ? NullNode.instance : primarySource.deepCopy());
        return contextual;
    }

    private int alignedArrayLength(
            ObjectNode sources, JsonNode primarySource,
            String arrayAlignment, int maximumArrayItems) {
        List<Integer> lengths = new ArrayList<>();
        sources.properties().forEach(entry -> {
            if (!entry.getValue().isArray()) return;
            int size = entry.getValue().size();
            if (size > maximumArrayItems) {
                throw new IllegalArgumentException(
                        "数据来源 " + entry.getKey() + " 的数组元素数量超过上限 "
                                + maximumArrayItems);
            }
            lengths.add(size);
        });
        if (lengths.isEmpty()) return primarySource.size();
        if ("STRICT".equals(arrayAlignment)
                && lengths.stream().anyMatch(length -> length != primarySource.size())) {
            throw new IllegalArgumentException("多个数组长度不一致，请调整对齐方式或补齐数据");
        }
        return switch (arrayAlignment) {
            case "SHORTEST" -> lengths.stream().min(Integer::compareTo).orElse(0);
            case "LONGEST" -> lengths.stream().max(Integer::compareTo).orElse(primarySource.size());
            default -> primarySource.size();
        };
    }

    private JsonNode keyedArrayItem(
            JsonNode array, JsonNode primaryKey, String alignmentPath, String sourceKey) {
        if (transformNull(primaryKey)) {
            throw new IllegalArgumentException("主要数组缺少关联字段 " + alignmentPath);
        }
        JsonNode match = MissingNode.getInstance();
        for (JsonNode item : array) {
            if (!transformValuesEqual(
                    readTransformPath(item, alignmentPath), primaryKey)) continue;
            if (!match.isMissingNode()) {
                throw new IllegalArgumentException(
                        "数据来源 " + sourceKey + " 的关联字段存在重复值: "
                                + transformText(primaryKey));
            }
            match = item;
        }
        return match;
    }

    private JsonNode transformRootValue(
            JsonNode source, ObjectNode sources, JsonNode rules, int maximumArrayItems) {
        JsonNode rule = rules.get(0);
        String operation = rule.path("operation").asText("COPY");
        JsonNode original;
        ObjectNode contextualSources = transformSourcesForIndex(
                sources, source, null, "PRIMARY", "");
        JsonNode ruleSource = transformRuleSource(source, contextualSources, rule);
        if ("CONSTANT".equals(operation)) {
            original = rule.path("value");
        } else {
            original = readTransformPath(ruleSource, rule.path("sourcePath").asText(""));
        }
        JsonNode value = applyDefault(rule, original);
        if (value == null || value.isMissingNode()) {
            if (rule.path("required").asBoolean(false)) {
                throw transformFailure("$", null, List.of(), "来源字段不存在", null);
            }
            return NullNode.instance;
        }
        try {
            return applyOperation(operation, value, rule,
                    ruleSource, contextualSources, maximumArrayItems);
        } catch (RuntimeException exception) {
            JsonNode fallback = recoverTransformError(rule, original, exception);
            if (fallback == null) {
                throw transformFailure("$", null, List.of(), exception.getMessage(), exception);
            }
            return fallback;
        }
    }

    private JsonNode transformRuleSource(
            JsonNode primarySource, ObjectNode sources, JsonNode rule) {
        String sourceKey = rule.path("sourceKey").asText("source");
        if (sourceKey.isBlank() || "source".equals(sourceKey)) return primarySource;
        return sources.path(sourceKey);
    }

    private void walkTransformSource(
            JsonNode current, List<TransformPathToken> sourceTokens, int tokenIndex,
            List<Integer> indexes, int wildcardDepth, ObjectNode output,
            List<TransformPathToken> targetTokens, JsonNode rule, Integer outerArrayIndex,
            int maximumArrayItems, JsonNode ruleSource, ObjectNode sources) {
        if (tokenIndex >= sourceTokens.size()) {
            applyTransformLeaf(output, targetTokens, indexes, rule,
                    current == null ? MissingNode.getInstance() : current,
                    outerArrayIndex, ruleSource, sources, maximumArrayItems);
            return;
        }
        TransformPathToken token = sourceTokens.get(tokenIndex);
        if (token.each()) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                if (rule.path("required").asBoolean(false)) {
                    throw transformFailure(rule.path("targetPath").asText(), outerArrayIndex,
                            indexes, "来源数组不存在", null);
                }
                return;
            }
            if (!current.isArray()) {
                throw transformFailure(rule.path("targetPath").asText(), outerArrayIndex,
                        indexes, "来源结构应为数组", null);
            }
            if (current.size() > maximumArrayItems) {
                throw transformFailure(rule.path("targetPath").asText(), outerArrayIndex,
                        indexes, "嵌套数组元素数量超过上限 " + maximumArrayItems, null);
            }
            ensureTransformTargetArray(output, targetTokens, indexes, wildcardDepth);
            for (int index = 0; index < current.size(); index++) {
                indexes.add(index);
                ensureTransformTargetItem(output, targetTokens, indexes, wildcardDepth);
                walkTransformSource(current.get(index), sourceTokens, tokenIndex + 1,
                        indexes, wildcardDepth + 1, output, targetTokens, rule,
                        outerArrayIndex, maximumArrayItems, ruleSource, sources);
                indexes.remove(indexes.size() - 1);
            }
            return;
        }
        JsonNode next = readTransformField(current, token.field());
        walkTransformSource(next, sourceTokens, tokenIndex + 1, indexes,
                wildcardDepth, output, targetTokens, rule, outerArrayIndex,
                maximumArrayItems, ruleSource, sources);
    }

    private JsonNode readTransformField(JsonNode source, String field) {
        if (source == null || source.isMissingNode() || source.isNull()) {
            return MissingNode.getInstance();
        }
        if (source.isArray() && field.chars().allMatch(Character::isDigit)) {
            try {
                return source.path(Integer.parseInt(field));
            } catch (NumberFormatException exception) {
                return MissingNode.getInstance();
            }
        }
        return source.path(field);
    }

    private void applyTransformLeaf(
            ObjectNode output, List<TransformPathToken> targetTokens, List<Integer> indexes,
            JsonNode rule, JsonNode original, Integer outerArrayIndex,
            JsonNode ruleSource, ObjectNode sources, int maximumArrayItems) {
        String targetPath = rule.path("targetPath").asText();
        JsonNode value = applyDefault(rule, original);
        if (value == null || value.isMissingNode()) {
            if (rule.path("required").asBoolean(false)) {
                throw transformFailure(targetPath, outerArrayIndex, indexes,
                        "来源字段不存在", null);
            }
            return;
        }
        try {
            JsonNode converted = applyOperation(
                    rule.path("operation").asText("COPY"), value, rule,
                    ruleSource, sources, maximumArrayItems);
            writeTransformLeaf(output, targetTokens, indexes, converted);
        } catch (RuntimeException exception) {
            JsonNode fallback = recoverTransformError(rule, original, exception);
            if (fallback == null) {
                throw transformFailure(targetPath, outerArrayIndex, indexes,
                        exception.getMessage(), exception);
            }
            writeTransformLeaf(output, targetTokens, indexes, fallback);
        }
    }

    private void ensureTransformTargetArray(
            ObjectNode output, List<TransformPathToken> tokens,
            List<Integer> indexes, int targetWildcardDepth) {
        JsonNode current = output;
        int wildcardDepth = 0;
        int indexCursor = 0;
        for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
            TransformPathToken token = tokens.get(tokenIndex);
            TransformPathToken next = tokenIndex + 1 < tokens.size()
                    ? tokens.get(tokenIndex + 1) : null;
            if (!token.each()) {
                if (!current.isObject()) {
                    throw new IllegalArgumentException("输出路径与已有字段类型冲突");
                }
                ObjectNode object = (ObjectNode) current;
                JsonNode child = object.get(token.field());
                boolean array = next != null && next.each();
                if (child == null || child.isNull()) {
                    child = array ? object.putArray(token.field()) : object.putObject(token.field());
                } else if (array ? !child.isArray() : !child.isObject()) {
                    throw new IllegalArgumentException("输出路径与已有字段类型冲突");
                }
                current = child;
                continue;
            }
            if (!current.isArray()) {
                throw new IllegalArgumentException("输出路径与已有字段类型冲突");
            }
            if (wildcardDepth == targetWildcardDepth) return;
            if (indexCursor >= indexes.size()) {
                throw new IllegalArgumentException("输出数组层级与来源不一致");
            }
            ArrayNode array = (ArrayNode) current;
            int itemIndex = indexes.get(indexCursor++);
            ensureArraySize(array, itemIndex + 1);
            JsonNode child = array.get(itemIndex);
            boolean nestedArray = next != null && next.each();
            if (child == null || child.isNull()) {
                child = nestedArray ? JsonNodeFactory.instance.arrayNode()
                        : JsonNodeFactory.instance.objectNode();
                array.set(itemIndex, child);
            } else if (nestedArray ? !child.isArray() : !child.isObject()) {
                throw new IllegalArgumentException("输出路径与已有字段类型冲突");
            }
            current = child;
            wildcardDepth++;
        }
    }

    private void ensureTransformTargetItem(
            ObjectNode output, List<TransformPathToken> tokens,
            List<Integer> indexes, int targetWildcardDepth) {
        JsonNode current = output;
        int wildcardDepth = 0;
        int indexCursor = 0;
        for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
            TransformPathToken token = tokens.get(tokenIndex);
            TransformPathToken next = tokenIndex + 1 < tokens.size()
                    ? tokens.get(tokenIndex + 1) : null;
            if (!token.each()) {
                if (!current.isObject()) {
                    throw new IllegalArgumentException("输出路径与已有字段类型冲突");
                }
                ObjectNode object = (ObjectNode) current;
                JsonNode child = object.get(token.field());
                boolean array = next != null && next.each();
                if (child == null || child.isNull()) {
                    child = array ? object.putArray(token.field()) : object.putObject(token.field());
                } else if (array ? !child.isArray() : !child.isObject()) {
                    throw new IllegalArgumentException("输出路径与已有字段类型冲突");
                }
                current = child;
                continue;
            }
            if (!current.isArray() || indexCursor >= indexes.size()) {
                throw new IllegalArgumentException("输出数组层级与来源不一致");
            }
            ArrayNode array = (ArrayNode) current;
            int itemIndex = indexes.get(indexCursor++);
            ensureArraySize(array, itemIndex + 1);
            if (wildcardDepth == targetWildcardDepth && next == null) return;
            JsonNode child = array.get(itemIndex);
            boolean nestedArray = next != null && next.each();
            if (child == null || child.isNull()) {
                child = nestedArray ? JsonNodeFactory.instance.arrayNode()
                        : JsonNodeFactory.instance.objectNode();
                array.set(itemIndex, child);
            } else if (nestedArray ? !child.isArray() : !child.isObject()) {
                throw new IllegalArgumentException("输出路径与已有字段类型冲突");
            }
            if (wildcardDepth == targetWildcardDepth) return;
            current = child;
            wildcardDepth++;
        }
    }

    private void writeTransformLeaf(
            ObjectNode output, List<TransformPathToken> tokens,
            List<Integer> indexes, JsonNode value) {
        JsonNode current = output;
        int indexCursor = 0;
        for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
            TransformPathToken token = tokens.get(tokenIndex);
            TransformPathToken next = tokenIndex + 1 < tokens.size()
                    ? tokens.get(tokenIndex + 1) : null;
            boolean leaf = next == null;
            if (!token.each()) {
                if (!current.isObject()) {
                    throw new IllegalArgumentException("输出路径与已有字段类型冲突");
                }
                ObjectNode object = (ObjectNode) current;
                if (leaf) {
                    object.set(token.field(), value == null ? NullNode.instance : value.deepCopy());
                    return;
                }
                JsonNode child = object.get(token.field());
                boolean array = next.each();
                if (child == null || child.isNull()) {
                    child = array ? object.putArray(token.field()) : object.putObject(token.field());
                } else if (array ? !child.isArray() : !child.isObject()) {
                    throw new IllegalArgumentException("输出路径与已有字段类型冲突");
                }
                current = child;
                continue;
            }
            if (!current.isArray() || indexCursor >= indexes.size()) {
                throw new IllegalArgumentException("输出数组层级与来源不一致");
            }
            ArrayNode array = (ArrayNode) current;
            int itemIndex = indexes.get(indexCursor++);
            ensureArraySize(array, itemIndex + 1);
            if (leaf) {
                array.set(itemIndex, value == null ? NullNode.instance : value.deepCopy());
                return;
            }
            JsonNode child = array.get(itemIndex);
            boolean nestedArray = next.each();
            if (child == null || child.isNull()) {
                child = nestedArray ? JsonNodeFactory.instance.arrayNode()
                        : JsonNodeFactory.instance.objectNode();
                array.set(itemIndex, child);
            } else if (nestedArray ? !child.isArray() : !child.isObject()) {
                throw new IllegalArgumentException("输出路径与已有字段类型冲突");
            }
            current = child;
        }
    }

    private void ensureArraySize(ArrayNode array, int size) {
        while (array.size() < size) array.add(NullNode.instance);
    }

    private JsonNode applyDefault(JsonNode rule, JsonNode value) {
        String defaultWhen = rule.path("defaultWhen").asText("NEVER");
        boolean missing = value == null || value.isMissingNode();
        boolean nullValue = value != null && value.isNull();
        boolean blank = value != null && value.isTextual() && value.asText().isBlank();
        boolean useDefault = switch (defaultWhen) {
            case "MISSING" -> missing;
            case "NULL" -> nullValue;
            case "BLANK" -> blank;
            case "NULL_OR_BLANK" -> nullValue || blank;
            default -> false;
        };
        if (!useDefault) {
            return value == null ? MissingNode.getInstance() : value;
        }
        return rule.has("defaultValue")
                ? rule.get("defaultValue").deepCopy() : NullNode.instance;
    }

    private JsonNode applyOperation(
            String operation, JsonNode value, JsonNode rule,
            JsonNode ruleSource, ObjectNode sources, int maximumArrayItems) {
        return switch (operation) {
            case "COPY", "CONSTANT" -> value.deepCopy();
            case "TO_STRING" -> JsonNodeFactory.instance.textNode(
                    value.isContainerNode() ? value.toString() : value.asText());
            case "TO_INTEGER" -> JsonNodeFactory.instance.numberNode(toLongExact(value));
            case "TO_NUMBER" -> JsonNodeFactory.instance.numberNode(toDecimal(value));
            case "TO_BOOLEAN" -> JsonNodeFactory.instance.booleanNode(toBoolean(value));
            case "TRIM" -> JsonNodeFactory.instance.textNode(value.asText().trim());
            case "UPPERCASE" -> JsonNodeFactory.instance.textNode(
                    value.asText().toUpperCase(Locale.ROOT));
            case "LOWERCASE" -> JsonNodeFactory.instance.textNode(
                    value.asText().toLowerCase(Locale.ROOT));
            case "ARRAY_JOIN" -> joinArray(value, rule.path("separator").asText(","));
            case "ARRAY_LENGTH" -> JsonNodeFactory.instance.numberNode(containerLength(value));
            case "CONCAT" -> concatenateFields(ruleSource, value, rule);
            case "DATE_FORMAT" -> formatDate(value, rule);
            case "ARRAY_FILTER" -> filterArray(value, rule, maximumArrayItems);
            case "ARRAY_FLATTEN" -> flattenArray(
                    value, rule.path("depth").asInt(1), maximumArrayItems);
            case "ARRAY_SORT" -> sortArray(value, rule, maximumArrayItems);
            case "ARRAY_DISTINCT" -> distinctArray(
                    value, rule.path("distinctPath").asText(""), maximumArrayItems);
            case "ARRAY_GROUP" -> groupArray(value, rule, maximumArrayItems);
            case "ARRAY_AGGREGATE" -> aggregateArray(value, rule, maximumArrayItems);
            case "TEMPLATE" -> renderTemplate(rule.path("template").asText(""),
                    value, ruleSource, sources);
            case "EXPRESSION" -> coerceExpressionResult(
                    WorkflowTransformExpressionEvaluator.evaluate(
                            rule.path("expression").asText(""), value, ruleSource, sources),
                    rule.path("resultType").asText("string"));
            default -> throw new IllegalArgumentException("不支持的处理方式: " + operation);
        };
    }

    private JsonNode concatenateFields(JsonNode source, JsonNode value, JsonNode rule) {
        List<String> values = new ArrayList<>();
        JsonNode sourcePaths = rule.path("sourcePaths");
        if (sourcePaths.isArray() && !sourcePaths.isEmpty()) {
            sourcePaths.forEach(path -> values.add(transformText(
                    readTransformPath(source, path.asText("")))));
        } else {
            values.add(transformText(value));
        }
        return JsonNodeFactory.instance.textNode(
                rule.path("prefix").asText("")
                        + String.join(rule.path("separator").asText(""), values)
                        + rule.path("suffix").asText(""));
    }

    private String transformText(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return "";
        return value.isContainerNode() ? value.toString() : value.asText();
    }

    private JsonNode coerceExpressionResult(JsonNode value, String resultType) {
        return switch (resultType) {
            case "string" -> JsonNodeFactory.instance.textNode(transformText(value));
            case "integer" -> JsonNodeFactory.instance.numberNode(toLongExact(value));
            case "number" -> JsonNodeFactory.instance.numberNode(toDecimal(value));
            case "boolean" -> JsonNodeFactory.instance.booleanNode(toBoolean(value));
            case "object" -> {
                if (!value.isObject()) throw new IllegalArgumentException("表达式结果不是对象");
                yield value.deepCopy();
            }
            case "array" -> {
                if (!value.isArray()) throw new IllegalArgumentException("表达式结果不是数组");
                yield value.deepCopy();
            }
            case "null" -> {
                if (!value.isNull()) throw new IllegalArgumentException("表达式结果不是空值");
                yield NullNode.instance;
            }
            default -> throw new IllegalArgumentException("表达式结果类型无效: " + resultType);
        };
    }

    private JsonNode formatDate(JsonNode value, JsonNode rule) {
        ZoneId zone;
        try {
            zone = ZoneId.of(rule.path("timezone").asText("Asia/Shanghai"));
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("时区无效", exception);
        }
        ZonedDateTime dateTime = parseTransformDate(
                value, rule.path("inputFormat").asText(""), zone);
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                    rule.path("outputFormat").asText("yyyy-MM-dd HH:mm:ss"), Locale.ROOT);
            return JsonNodeFactory.instance.textNode(formatter.format(dateTime));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("输出日期格式无效", exception);
        }
    }

    private ZonedDateTime parseTransformDate(JsonNode value, String inputFormat, ZoneId zone) {
        if (value.isNumber()) {
            long timestamp = value.longValue();
            Instant instant = Math.abs(timestamp) < 100_000_000_000L
                    ? Instant.ofEpochSecond(timestamp) : Instant.ofEpochMilli(timestamp);
            return instant.atZone(zone);
        }
        String text = value.asText().trim();
        try {
            if (!inputFormat.isBlank()) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(inputFormat, Locale.ROOT);
                try {
                    return ZonedDateTime.parse(text, formatter).withZoneSameInstant(zone);
                } catch (DateTimeParseException ignored) {
                    try {
                        return OffsetDateTime.parse(text, formatter).atZoneSameInstant(zone);
                    } catch (DateTimeParseException ignoredOffset) {
                        try {
                            return LocalDateTime.parse(text, formatter).atZone(zone);
                        } catch (DateTimeParseException ignoredDateTime) {
                            return LocalDate.parse(text, formatter).atStartOfDay(zone);
                        }
                    }
                }
            }
            try {
                return Instant.parse(text).atZone(zone);
            } catch (DateTimeParseException ignored) {
                try {
                    return OffsetDateTime.parse(text).atZoneSameInstant(zone);
                } catch (DateTimeParseException ignoredOffset) {
                    try {
                        return LocalDateTime.parse(text).atZone(zone);
                    } catch (DateTimeParseException ignoredDateTime) {
                        return LocalDate.parse(text).atStartOfDay(zone);
                    }
                }
            }
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("无法识别日期时间", exception);
        }
    }

    private JsonNode filterArray(JsonNode value, JsonNode rule, int maximumArrayItems) {
        requireArray(value, "数组筛选", maximumArrayItems);
        ArrayNode output = JsonNodeFactory.instance.arrayNode();
        String path = rule.path("filterPath").asText("");
        String operator = rule.path("filterOperator").asText("EQ");
        JsonNode expected = rule.has("filterValue") ? rule.get("filterValue") : NullNode.instance;
        value.forEach(item -> {
            JsonNode actual = readTransformPath(item, path);
            if (matchesFilter(actual, operator, expected)) output.add(item.deepCopy());
        });
        return output;
    }

    private boolean matchesFilter(JsonNode actual, String operator, JsonNode expected) {
        boolean empty = actual == null || actual.isMissingNode() || actual.isNull();
        return switch (operator) {
            case "IS_NULL" -> empty;
            case "NOT_NULL" -> !empty;
            case "EQ" -> transformValuesEqual(actual, expected);
            case "NE" -> !transformValuesEqual(actual, expected);
            case "GT" -> compareTransformValues(actual, expected) > 0;
            case "GTE" -> compareTransformValues(actual, expected) >= 0;
            case "LT" -> compareTransformValues(actual, expected) < 0;
            case "LTE" -> compareTransformValues(actual, expected) <= 0;
            case "CONTAINS" -> transformText(actual).contains(transformText(expected));
            case "STARTS_WITH" -> transformText(actual).startsWith(transformText(expected));
            case "ENDS_WITH" -> transformText(actual).endsWith(transformText(expected));
            default -> throw new IllegalArgumentException("不支持的筛选条件: " + operator);
        };
    }

    private JsonNode flattenArray(JsonNode value, int depth, int maximumArrayItems) {
        requireArray(value, "数组展开", maximumArrayItems);
        ArrayNode output = JsonNodeFactory.instance.arrayNode();
        flattenArrayItems(value, Math.max(1, Math.min(10, depth)), output, maximumArrayItems);
        return output;
    }

    private void flattenArrayItems(
            JsonNode value, int depth, ArrayNode output, int maximumArrayItems) {
        value.forEach(item -> {
            if (depth > 0 && item.isArray()) {
                flattenArrayItems(item, depth - 1, output, maximumArrayItems);
            } else {
                if (output.size() >= maximumArrayItems) {
                    throw new IllegalArgumentException(
                            "数组展开后的元素数量超过上限 " + maximumArrayItems);
                }
                output.add(item.deepCopy());
            }
        });
    }

    private JsonNode sortArray(JsonNode value, JsonNode rule, int maximumArrayItems) {
        requireArray(value, "数组排序", maximumArrayItems);
        String path = rule.path("sortPath").asText("");
        boolean descending = "DESC".equals(rule.path("sortDirection").asText("ASC"));
        boolean nullsFirst = "FIRST".equals(rule.path("nulls").asText("LAST"));
        List<JsonNode> items = new ArrayList<>();
        value.forEach(item -> items.add(item.deepCopy()));
        Comparator<JsonNode> comparator = (left, right) -> {
            JsonNode leftValue = readTransformPath(left, path);
            JsonNode rightValue = readTransformPath(right, path);
            boolean leftNull = transformNull(leftValue);
            boolean rightNull = transformNull(rightValue);
            if (leftNull || rightNull) {
                if (leftNull && rightNull) return 0;
                return (leftNull == nullsFirst) ? -1 : 1;
            }
            int compared = compareTransformValues(leftValue, rightValue);
            return descending ? -compared : compared;
        };
        items.sort(comparator);
        ArrayNode output = JsonNodeFactory.instance.arrayNode();
        items.forEach(output::add);
        return output;
    }

    private JsonNode distinctArray(JsonNode value, String path, int maximumArrayItems) {
        requireArray(value, "数组去重", maximumArrayItems);
        Set<JsonNode> seen = new LinkedHashSet<>();
        ArrayNode output = JsonNodeFactory.instance.arrayNode();
        value.forEach(item -> {
            JsonNode key = readTransformPath(item, path);
            if (seen.add(key.deepCopy())) output.add(item.deepCopy());
        });
        return output;
    }

    private JsonNode groupArray(JsonNode value, JsonNode rule, int maximumArrayItems) {
        requireArray(value, "数组分组", maximumArrayItems);
        String path = rule.path("groupPath").asText("");
        Map<JsonNode, ArrayNode> groups = new LinkedHashMap<>();
        value.forEach(item -> {
            JsonNode key = readTransformPath(item, path);
            groups.computeIfAbsent(key.deepCopy(), ignored -> JsonNodeFactory.instance.arrayNode())
                    .add(item.deepCopy());
        });
        ArrayNode output = JsonNodeFactory.instance.arrayNode();
        groups.forEach((key, items) -> {
            ObjectNode group = output.addObject();
            group.set("key", key.deepCopy());
            group.set("items", items);
            String aggregate = rule.path("groupAggregate").asText("NONE");
            if (!"NONE".equals(aggregate)) {
                group.set("value", aggregateArray(items, aggregate,
                        rule.path("groupAggregatePath").asText(""), maximumArrayItems));
            }
        });
        return output;
    }

    private JsonNode aggregateArray(JsonNode value, JsonNode rule, int maximumArrayItems) {
        return aggregateArray(value, rule.path("aggregate").asText("COUNT"),
                rule.path("aggregatePath").asText(""), maximumArrayItems);
    }

    private JsonNode aggregateArray(
            JsonNode value, String aggregate, String path, int maximumArrayItems) {
        requireArray(value, "数组聚合", maximumArrayItems);
        if ("COUNT".equals(aggregate)) {
            return JsonNodeFactory.instance.numberNode(value.size());
        }
        List<BigDecimal> numbers = new ArrayList<>();
        value.forEach(item -> {
            JsonNode current = readTransformPath(item, path);
            if (!transformNull(current)) numbers.add(toDecimal(current));
        });
        if (numbers.isEmpty()) return NullNode.instance;
        BigDecimal result = switch (aggregate) {
            case "SUM" -> numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            case "AVG" -> numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(numbers.size()), MathContext.DECIMAL128);
            case "MIN" -> numbers.stream().min(BigDecimal::compareTo).orElseThrow();
            case "MAX" -> numbers.stream().max(BigDecimal::compareTo).orElseThrow();
            default -> throw new IllegalArgumentException("不支持的聚合方式: " + aggregate);
        };
        return JsonNodeFactory.instance.numberNode(result.stripTrailingZeros());
    }

    private JsonNode renderTemplate(
            String template, JsonNode value, JsonNode source, ObjectNode sources) {
        Matcher matcher = TRANSFORM_TEMPLATE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String path = matcher.group(1);
            JsonNode replacement;
            if ("value".equals(path) || path.startsWith("value.")) {
                replacement = readTransformPath(value,
                        path.length() == 5 ? "" : path.substring(6));
            } else if ("source".equals(path) || path.startsWith("source.")) {
                replacement = readTransformPath(source,
                        path.length() == 6 ? "" : path.substring(7));
            } else {
                int dot = path.indexOf('.');
                String possibleSource = dot < 0 ? path : path.substring(0, dot);
                if (sources.has(possibleSource)) {
                    replacement = readTransformPath(sources.path(possibleSource),
                            dot < 0 ? "" : path.substring(dot + 1));
                } else {
                    replacement = readTransformPath(source, path);
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(transformText(replacement)));
        }
        matcher.appendTail(result);
        return JsonNodeFactory.instance.textNode(result.toString());
    }

    private JsonNode readTransformPath(JsonNode source, String path) {
        JsonNode current = source == null ? MissingNode.getInstance() : source;
        if (path == null || path.isBlank()) return current;
        for (String part : path.split("\\.")) {
            if (current.isArray() && part.chars().allMatch(Character::isDigit)) {
                current = current.path(Integer.parseInt(part));
            } else {
                current = current.path(part);
            }
            if (current.isMissingNode()) return current;
        }
        return current;
    }

    private void requireArray(JsonNode value, String operation, int maximumArrayItems) {
        if (!value.isArray()) throw new IllegalArgumentException(operation + "要求来源字段是数组");
        if (value.size() > maximumArrayItems) {
            throw new IllegalArgumentException(
                    operation + "的数组元素数量超过上限 " + maximumArrayItems);
        }
    }

    private boolean transformNull(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull();
    }

    private boolean transformValuesEqual(JsonNode left, JsonNode right) {
        if (transformNull(left) || transformNull(right)) {
            return transformNull(left) && transformNull(right);
        }
        if (left.isNumber() && right.isNumber()) {
            return left.decimalValue().compareTo(right.decimalValue()) == 0;
        }
        return left.equals(right);
    }

    private int compareTransformValues(JsonNode left, JsonNode right) {
        if (transformNull(left) || transformNull(right)) {
            throw new IllegalArgumentException("不能比较空值");
        }
        if (left.isNumber() && right.isNumber()) {
            return left.decimalValue().compareTo(right.decimalValue());
        }
        if (left.isTextual() && right.isTextual()) {
            return left.asText().compareTo(right.asText());
        }
        if (left.isBoolean() && right.isBoolean()) {
            return Boolean.compare(left.asBoolean(), right.asBoolean());
        }
        throw new IllegalArgumentException("排序和比较只支持数字、文本或是/否值");
    }

    private long toLongExact(JsonNode value) {
        try {
            if (value.isIntegralNumber()) {
                return value.longValue();
            }
            return new BigDecimal(value.asText()).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("无法转换为整数", exception);
        }
    }

    private BigDecimal toDecimal(JsonNode value) {
        try {
            return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("无法转换为数字", exception);
        }
    }

    private boolean toBoolean(JsonNode value) {
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isNumber()) {
            return value.decimalValue().compareTo(BigDecimal.ZERO) != 0;
        }
        return switch (value.asText().trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> throw new IllegalArgumentException("无法转换为布尔值");
        };
    }

    private JsonNode joinArray(JsonNode value, String separator) {
        if (!value.isArray()) {
            throw new IllegalArgumentException("合并文本要求来源字段是数组");
        }
        List<String> values = new ArrayList<>();
        value.forEach(item -> values.add(item.isContainerNode() ? item.toString() : item.asText()));
        return JsonNodeFactory.instance.textNode(String.join(separator, values));
    }

    private int containerLength(JsonNode value) {
        if (value.isTextual()) {
            return value.asText().codePointCount(0, value.asText().length());
        }
        if (value.isArray() || value.isObject()) {
            return value.size();
        }
        throw new IllegalArgumentException("统计数量仅支持数组、对象或文本");
    }

    private JsonNode recoverTransformError(JsonNode rule, JsonNode original, RuntimeException error) {
        return switch (rule.path("onError").asText("FAIL")) {
            case "NULL" -> NullNode.instance;
            case "DEFAULT" -> rule.has("defaultValue")
                    ? rule.get("defaultValue").deepCopy() : NullNode.instance;
            case "KEEP" -> original == null || original.isMissingNode()
                    ? NullNode.instance : original.deepCopy();
            default -> null;
        };
    }

    private IllegalArgumentException transformFailure(
            String targetPath, Integer arrayIndex, List<Integer> nestedIndexes,
            String detail, Throwable cause) {
        List<Integer> indexes = new ArrayList<>();
        if (arrayIndex != null) indexes.add(arrayIndex);
        if (nestedIndexes != null) indexes.addAll(nestedIndexes);
        String location = indexes.isEmpty() ? "" : "（" + indexes.stream()
                .map(index -> "第 " + (index + 1) + " 项")
                .reduce((left, right) -> left + " > " + right).orElse("") + "）";
        String message = "输出字段 " + targetPath + location + " 转换失败: " + detail;
        return cause == null
                ? new IllegalArgumentException(message)
                : new IllegalArgumentException(message, cause);
    }

    private void validateTargetPath(String path) {
        validateTargetPath(path, false);
    }

    private void validateTargetPath(String path, boolean allowRoot) {
        if (path == null || !path.matches(TRANSFORM_TARGET_PATH_PATTERN)
                || (!allowRoot && "$".equals(path))) {
            throw new IllegalArgumentException("输出字段路径无效: " + path);
        }
    }

    private void validateSourcePath(String path) {
        if (path == null || !path.matches(TRANSFORM_SOURCE_PATH_PATTERN)) {
            throw new IllegalArgumentException("来源字段路径无效: " + path);
        }
    }

    private void validateTransformRules(JsonNode rules, String mode) {
        boolean rootMode = "VALUE".equals(mode);
        if (rules == null || !rules.isArray()) {
            if (rootMode) {
                throw new IllegalArgumentException("直接转换整份数据时必须配置一条规则");
            }
            return;
        }
        if (rootMode && rules.size() != 1) {
            throw new IllegalArgumentException("直接转换整份数据时必须且只能配置一条规则");
        }
        List<String> paths = new ArrayList<>();
        for (JsonNode rule : rules) {
            String path = rule.path("targetPath").asText("").trim();
            validateTargetPath(path, rootMode);
            if (rootMode && !"$".equals(path)) {
                throw new IllegalArgumentException("直接转换整份数据的输出位置必须为 $");
            }
            String operation = rule.path("operation").asText("COPY");
            String sourcePath = rule.path("sourcePath").asText("");
            if (!"CONSTANT".equals(operation)) {
                validateSourcePath(sourcePath);
                if (rootMode && sourcePath.contains("[]")) {
                    throw new IllegalArgumentException("根级转换不支持数组通配路径，请直接选择整个数组");
                }
                int sourceWildcards = countWildcards(parseTransformPath(sourcePath));
                int targetWildcards = countWildcards(parseTransformPath(path));
                if (!rootMode && sourceWildcards != targetWildcards) {
                    throw new IllegalArgumentException(
                            "来源和输出路径的数组层级必须一致: " + sourcePath + " → " + path);
                }
            } else if (path.contains("[]")) {
                throw new IllegalArgumentException("固定值不能直接写入数组通配路径: " + path);
            }
            validateAdvancedTransformRule(rule);
            for (String existing : paths) {
                if (transformPathsConflict(existing, path)) {
                    throw new IllegalArgumentException(
                            "输出字段路径冲突: " + existing + " 与 " + path);
                }
            }
            paths.add(path);
        }
    }

    private void validateArrayAlignment(String alignment, String alignmentPath) {
        if (!Set.of("PRIMARY", "STRICT", "SHORTEST", "LONGEST", "KEYED")
                .contains(alignment)) {
            throw new IllegalArgumentException("多个数组的对齐方式无效");
        }
        if ("KEYED".equals(alignment)) {
            validateSimpleTransformPath(alignmentPath, "数组关联字段");
            if (alignmentPath.isBlank()) {
                throw new IllegalArgumentException("按字段关联数组时必须选择关联字段");
            }
        }
    }

    private void validateAdvancedTransformRule(JsonNode rule) {
        String operation = rule.path("operation").asText("COPY");
        Set<String> operations = Set.of(
                "COPY", "CONSTANT", "TO_STRING", "TO_INTEGER", "TO_NUMBER",
                "TO_BOOLEAN", "TRIM", "UPPERCASE", "LOWERCASE", "ARRAY_JOIN",
                "ARRAY_LENGTH", "CONCAT", "DATE_FORMAT", "ARRAY_FILTER",
                "ARRAY_FLATTEN", "ARRAY_SORT", "ARRAY_DISTINCT", "ARRAY_GROUP",
                "ARRAY_AGGREGATE", "TEMPLATE", "EXPRESSION");
        if (!operations.contains(operation)) {
            throw new IllegalArgumentException("不支持的处理方式: " + operation);
        }
        String sourceKey = rule.path("sourceKey").asText("source");
        if (!sourceKey.matches("^[A-Za-z_][A-Za-z0-9_]{0,39}$")) {
            throw new IllegalArgumentException("数据来源标识无效: " + sourceKey);
        }
        if ("CONCAT".equals(operation) && rule.path("sourcePaths").isArray()) {
            rule.path("sourcePaths").forEach(path -> validateSimpleTransformPath(
                    path.asText(""), "拼接字段"));
        }
        if ("DATE_FORMAT".equals(operation)
                && rule.path("outputFormat").asText("").isBlank()) {
            throw new IllegalArgumentException("日期格式化必须填写输出格式");
        }
        if ("ARRAY_FILTER".equals(operation)) {
            validateSimpleTransformPath(rule.path("filterPath").asText(""), "筛选字段");
            Set<String> filters = Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE",
                    "CONTAINS", "STARTS_WITH", "ENDS_WITH", "IS_NULL", "NOT_NULL");
            if (!filters.contains(rule.path("filterOperator").asText("EQ"))) {
                throw new IllegalArgumentException("数组筛选方式无效");
            }
        }
        if ("ARRAY_FLATTEN".equals(operation)
                && (rule.path("depth").asInt(1) < 1 || rule.path("depth").asInt(1) > 10)) {
            throw new IllegalArgumentException("数组展开层数必须在 1 到 10 之间");
        }
        if ("ARRAY_SORT".equals(operation)) {
            validateSimpleTransformPath(rule.path("sortPath").asText(""), "排序字段");
        }
        if ("ARRAY_DISTINCT".equals(operation)) {
            validateSimpleTransformPath(rule.path("distinctPath").asText(""), "去重字段");
        }
        if ("ARRAY_GROUP".equals(operation)) {
            validateSimpleTransformPath(rule.path("groupPath").asText(""), "分组字段");
            String groupAggregate = rule.path("groupAggregate").asText("NONE");
            if (!Set.of("NONE", "COUNT", "SUM", "AVG", "MIN", "MAX")
                    .contains(groupAggregate)) {
                throw new IllegalArgumentException("分组聚合方式无效");
            }
            if (!Set.of("NONE", "COUNT").contains(groupAggregate)) {
                validateSimpleTransformPath(rule.path("groupAggregatePath").asText(""),
                        "分组聚合字段");
            }
        }
        if ("ARRAY_AGGREGATE".equals(operation)) {
            String aggregate = rule.path("aggregate").asText("COUNT");
            if (!Set.of("COUNT", "SUM", "AVG", "MIN", "MAX").contains(aggregate)) {
                throw new IllegalArgumentException("数组聚合方式无效");
            }
            if (!"COUNT".equals(aggregate)) {
                validateSimpleTransformPath(
                        rule.path("aggregatePath").asText(""), "聚合字段");
            }
        }
        if ("TEMPLATE".equals(operation) && rule.path("template").asText("").isBlank()) {
            throw new IllegalArgumentException("文本模板不能为空");
        }
        if ("EXPRESSION".equals(operation)
                && rule.path("expression").asText("").isBlank()) {
            throw new IllegalArgumentException("受限表达式不能为空");
        }
        String onError = rule.path("onError").asText("FAIL");
        if ("DEFAULT".equals(onError) && rule.has("defaultValue")
                && !rule.path("defaultValue").isNull()) {
            validateFallbackType(rule, rule.get("defaultValue"), "转换失败默认值");
        }
        String defaultWhen = rule.path("defaultWhen").asText("NEVER");
        if (!"NEVER".equals(defaultWhen) && rule.has("defaultValue")
                && !rule.path("defaultValue").isNull()) {
            validateFallbackType(rule, rule.get("defaultValue"), "字段默认值");
        }
    }

    private void validateFallbackType(JsonNode rule, JsonNode value, String label) {
        String expected = operationResultType(rule);
        String actual = jsonType(value);
        boolean compatible = expected.equals(actual)
                || "number".equals(expected) && "integer".equals(actual);
        if (!compatible) {
            throw new IllegalArgumentException(
                    label + "类型应为 " + expected + "，当前为 " + actual);
        }
    }

    private void validateSimpleTransformPath(String path, String label) {
        if (path == null || !path.matches(TRANSFORM_SIMPLE_PATH_PATTERN)) {
            throw new IllegalArgumentException(label + "路径无效: " + path);
        }
    }

    private List<TransformPathToken> parseTransformPath(String path) {
        List<TransformPathToken> tokens = new ArrayList<>();
        if (path == null || path.isBlank()) return tokens;
        for (String segment : path.split("\\.")) {
            int bracket = segment.indexOf('[');
            String field = bracket < 0 ? segment : segment.substring(0, bracket);
            if (!field.isBlank()) tokens.add(new TransformPathToken(field, false));
            String suffix = bracket < 0 ? "" : segment.substring(bracket);
            while (!suffix.isEmpty()) {
                if (!suffix.startsWith("[]")) {
                    throw new IllegalArgumentException("字段路径无效: " + path);
                }
                tokens.add(new TransformPathToken("", true));
                suffix = suffix.substring(2);
            }
        }
        return tokens;
    }

    private int countWildcards(List<TransformPathToken> tokens) {
        return (int) tokens.stream().filter(TransformPathToken::each).count();
    }

    private boolean transformPathsConflict(String left, String right) {
        List<TransformPathToken> leftTokens = parseTransformPath(left);
        List<TransformPathToken> rightTokens = parseTransformPath(right);
        int maximum = Math.min(leftTokens.size(), rightTokens.size());
        for (int index = 0; index < maximum; index++) {
            TransformPathToken leftToken = leftTokens.get(index);
            TransformPathToken rightToken = rightTokens.get(index);
            if (leftToken.equals(rightToken)) continue;
            return leftToken.each() != rightToken.each();
        }
        return true;
    }

    private ObjectNode transformOutputItemSchema(
            JsonNode config, List<String> diagnostics, Map<String, String> fieldSources) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        root.put("additionalProperties", config.path("preserveUnmapped").asBoolean(false));
        root.putObject("properties");
        JsonNode rules = config.path("rules");
        if (!rules.isArray()) {
            return root;
        }
        List<String> targetPaths = new ArrayList<>();
        for (JsonNode rule : rules) {
            String path = rule.path("targetPath").asText("").trim();
            try {
                validateTargetPath(path);
                String operation = rule.path("operation").asText("COPY");
                String sourcePath = rule.path("sourcePath").asText("");
                if (!"CONSTANT".equals(operation)) {
                    validateSourcePath(sourcePath);
                    if (countWildcards(parseTransformPath(sourcePath))
                            != countWildcards(parseTransformPath(path))) {
                        throw new IllegalArgumentException(
                                "来源和输出路径的数组层级必须一致: " + sourcePath + " → " + path);
                    }
                } else if (path.contains("[]")) {
                    throw new IllegalArgumentException("固定值不能直接写入数组通配路径: " + path);
                }
                validateAdvancedTransformRule(rule);
                if (targetPaths.stream().anyMatch(existing -> transformPathsConflict(existing, path))) {
                    throw new IllegalArgumentException("输出字段路径冲突: " + path);
                }
                targetPaths.add(path);
                String defaultWhen = rule.path("defaultWhen").asText("NEVER");
                boolean guaranteed = rule.path("required").asBoolean(false)
                        || "CONSTANT".equals(rule.path("operation").asText())
                        || "MISSING".equals(defaultWhen)
                        || rule.path("sourcePath").asText("").isBlank();
                addTransformSchemaPath(root, path, operationResultSchema(rule),
                        guaranteed);
                fieldSources.put("$." + path, "TRANSFORM_RULE");
            } catch (IllegalArgumentException exception) {
                diagnostics.add(exception.getMessage());
            }
        }
        return root;
    }

    private String operationResultType(JsonNode rule) {
        String operation = rule.path("operation").asText("COPY");
        return switch (operation) {
            case "TO_STRING", "TRIM", "UPPERCASE", "LOWERCASE", "ARRAY_JOIN",
                    "CONCAT", "DATE_FORMAT", "TEMPLATE" -> "string";
            case "TO_INTEGER", "ARRAY_LENGTH" -> "integer";
            case "TO_NUMBER" -> "number";
            case "ARRAY_AGGREGATE" -> "COUNT".equals(rule.path("aggregate").asText("COUNT"))
                    ? "integer" : "number";
            case "TO_BOOLEAN" -> "boolean";
            case "ARRAY_FILTER", "ARRAY_FLATTEN", "ARRAY_SORT", "ARRAY_DISTINCT",
                    "ARRAY_GROUP" -> "array";
            case "EXPRESSION" -> rule.path("resultType").asText("string");
            case "CONSTANT" -> rule.hasNonNull("resultType")
                    ? rule.path("resultType").asText() : jsonType(rule.get("value"));
            default -> rule.hasNonNull("resultType")
                    ? rule.path("resultType").asText("object")
                    : rule.path("sourceType").asText("object");
        };
    }

    private ObjectNode operationResultSchema(JsonNode rule) {
        String operation = rule.path("operation").asText("COPY");
        ObjectNode schema;
        if (rule.path("resultSchema").isObject()
                && rule.path("resultSchema").has("type")) {
            schema = ((ObjectNode) rule.path("resultSchema")).deepCopy();
        } else if ("CONSTANT".equals(operation) && rule.has("value")) {
            schema = inferTransformValueSchema(rule.get("value"), 0);
        } else {
            schema = JsonNodeFactory.instance.objectNode();
            String type = operationResultType(rule);
            schema.put("type", type);
            if ("object".equals(type)) schema.put("additionalProperties", true);
            if ("array".equals(type)) schema.putObject("items");
        }
        if ("ARRAY_AGGREGATE".equals(operation)
                && !"COUNT".equals(rule.path("aggregate").asText("COUNT"))) {
            appendSchemaType(schema, "null");
        }
        if ("ARRAY_GROUP".equals(operation)
                && !Set.of("NONE", "COUNT")
                        .contains(rule.path("groupAggregate").asText("NONE"))) {
            JsonNode valueSchema = schema.path("items").path("properties").path("value");
            if (valueSchema.isObject()) appendSchemaType((ObjectNode) valueSchema, "null");
        }
        boolean nullableFallback = "NULL".equals(rule.path("onError").asText("FAIL"))
                || ("DEFAULT".equals(rule.path("onError").asText("FAIL"))
                    && rule.path("defaultValue").isNull())
                || (!"NEVER".equals(rule.path("defaultWhen").asText("NEVER"))
                    && rule.path("defaultValue").isNull());
        if ("$".equals(rule.path("targetPath").asText())
                && !rule.path("required").asBoolean(false)
                && !"MISSING".equals(rule.path("defaultWhen").asText("NEVER"))) {
            nullableFallback = true;
        }
        if (nullableFallback) appendSchemaType(schema, "null");
        if ("KEEP".equals(rule.path("onError").asText("FAIL"))) {
            appendSchemaType(schema, rule.path("sourceType").asText("object"));
            if (schema.path("type").isArray()) {
                schema.remove(List.of("properties", "required", "items", "additionalProperties"));
            }
        }
        return schema;
    }

    private void appendSchemaType(ObjectNode schema, String type) {
        JsonNode current = schema.get("type");
        if (current == null || current.isMissingNode()) {
            schema.put("type", type);
            return;
        }
        if (current.isTextual()) {
            if (type.equals(current.asText())) return;
            ArrayNode types = JsonNodeFactory.instance.arrayNode();
            types.add(current.asText()).add(type);
            schema.set("type", types);
            return;
        }
        if (current.isArray()) {
            for (JsonNode item : current) if (type.equals(item.asText())) return;
            ((ArrayNode) current).add(type);
        }
    }

    private ObjectNode inferTransformValueSchema(JsonNode value, int depth) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        String type = jsonType(value);
        schema.put("type", type);
        if (depth >= 10) return schema;
        if (value != null && value.isObject()) {
            ObjectNode properties = schema.putObject("properties");
            ArrayNode required = schema.putArray("required");
            value.properties().forEach(entry -> {
                properties.set(entry.getKey(), inferTransformValueSchema(entry.getValue(), depth + 1));
                required.add(entry.getKey());
            });
            schema.put("additionalProperties", false);
        } else if (value != null && value.isArray()) {
            JsonNode sample = value.isEmpty() ? NullNode.instance : value.get(0);
            schema.set("items", value.isEmpty()
                    ? JsonNodeFactory.instance.objectNode()
                    : inferTransformValueSchema(sample, depth + 1));
        }
        return schema;
    }

    private String jsonType(JsonNode value) {
        if (value == null || value.isNull()) return "null";
        if (value.isObject()) return "object";
        if (value.isArray()) return "array";
        if (value.isIntegralNumber()) return "integer";
        if (value.isNumber()) return "number";
        if (value.isBoolean()) return "boolean";
        return "string";
    }

    private void addTransformSchemaPath(
            ObjectNode root, String path, ObjectNode resultSchema, boolean required) {
        List<TransformPathToken> tokens = parseTransformPath(path);
        ObjectNode current = root;
        for (int index = 0; index < tokens.size(); index++) {
            TransformPathToken token = tokens.get(index);
            TransformPathToken next = index + 1 < tokens.size() ? tokens.get(index + 1) : null;
            boolean leaf = next == null;
            if (token.each()) {
                ensureSchemaType(current, "array", path);
                ObjectNode items = current.has("items") && current.get("items").isObject()
                        ? (ObjectNode) current.get("items") : current.putObject("items");
                if (leaf) configureTransformLeafSchema(items, resultSchema, path);
                else ensureSchemaType(items, next.each() ? "array" : "object", path);
                current = items;
                continue;
            }
            ensureSchemaType(current, "object", path);
            ObjectNode properties = current.withObject("/properties");
            JsonNode existing = properties.get(token.field());
            ObjectNode field = existing != null && existing.isObject()
                    ? (ObjectNode) existing : properties.putObject(token.field());
            if (required) appendRequired(current, token.field());
            if (leaf) configureTransformLeafSchema(field, resultSchema, path);
            else ensureSchemaType(field, next.each() ? "array" : "object", path);
            current = field;
        }
    }

    private void ensureSchemaType(ObjectNode schema, String type, String path) {
        String existing = schema.path("type").asText("");
        if (!existing.isBlank() && !existing.equals(type)) {
            throw new IllegalArgumentException("输出字段路径类型冲突: " + path);
        }
        schema.put("type", type);
        if ("object".equals(type)) {
            schema.putIfAbsent("additionalProperties", JsonNodeFactory.instance.booleanNode(false));
            schema.withObject("/properties");
        }
        if ("array".equals(type) && !schema.has("items")) schema.putObject("items");
    }

    private void configureTransformLeafSchema(
            ObjectNode schema, ObjectNode resultSchema, String path) {
        JsonNode typeNode = resultSchema.get("type");
        String type = typeNode != null && typeNode.isTextual()
                ? typeNode.asText() : "";
        if (!type.isBlank()) ensureSchemaType(schema, type, path);
        schema.removeAll();
        schema.setAll(resultSchema.deepCopy());
        if (!schema.has("type")) schema.put("type", type.isBlank() ? "object" : type);
        if ("object".equals(type) && !schema.has("additionalProperties")) {
            schema.put("additionalProperties", true);
        }
        if ("array".equals(type) && !schema.has("items")) schema.putObject("items");
    }

    private void appendRequired(ObjectNode schema, String field) {
        ArrayNode required = schema.withArray("required");
        for (JsonNode item : required) {
            if (field.equals(item.asText())) return;
        }
        required.add(field);
    }

    private String transformFingerprint(JsonNode config) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(config.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成数据转换契约版本", exception);
        }
    }

    @Bean
    public WorkflowNodeHandler conditionWorkflowNodeHandler() {
        return passThrough("condition", "条件判断", "control", emptyConfigSchema());
    }

    @Bean
    public WorkflowNodeHandler parallelWorkflowNodeHandler() {
        return passThrough("parallel", "并行分支", "control", emptyConfigSchema());
    }

    @Bean
    public WorkflowNodeHandler joinWorkflowNodeHandler() {
        return passThrough("join", "分支汇聚", "control", joinConfigSchema());
    }

    @Bean
    public WorkflowNodeHandler loopWorkflowNodeHandler() {
        return passThrough("loop", "受控循环", "control",
                loopConfigSchema(), loopOutputSchema());
    }

    @Bean
    public WorkflowNodeHandler approvalWorkflowNodeHandler() {
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "approval", "1.0", "人工审批", "control",
                approvalConfigSchema(), JsonNodeFactory.instance.objectNode(),
                approvalOutputSchema(), WorkflowSideEffect.NONE, Set.of(),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.MOCKABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return markerHandler(descriptor);
    }

    @Bean
    public WorkflowNodeHandler subWorkflowNodeHandler() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putArray("required").add("definitionId").add("reviewedVersionId").add("versionPolicy").add("resultMode");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("definitionId").put("type", "integer").put("minimum", 1);
        properties.putObject("reviewedVersionId").put("type", "string")
                .put("minLength", 1).put("maxLength", 64);
        properties.putObject("versionPolicy").put("type", "string").putArray("enum").add("LATEST").add("PINNED");
        properties.putObject("resultMode").put("type", "string").putArray("enum").add("STOP").add("BRANCH").add("DETAILED");
        properties.putObject("maxWaitSeconds").put("type", "integer").put("minimum", 0).put("maximum", 2592000);
        properties.putObject("inputs").put("type", "object");
        schema.put("additionalProperties", false);
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "sub_workflow", "2.0", "子工作流", "control", schema,
                JsonNodeFactory.instance.objectNode(), subWorkflowOutputSchema(),
                WorkflowSideEffect.NONE, Set.of(),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return markerHandler(descriptor);
    }

    private WorkflowNodeHandler passThrough(
            String type, String name, String category, ObjectNode configSchema) {
        return passThrough(type, name, category, configSchema,
                JsonNodeFactory.instance.objectNode());
    }

    private WorkflowNodeHandler passThrough(
            String type, String name, String category,
            ObjectNode configSchema, ObjectNode outputSchema) {
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                type,
                "1.0",
                name,
                category,
                configSchema,
                JsonNodeFactory.instance.objectNode(),
                outputSchema,
                WorkflowSideEffect.NONE,
                Set.of(),
                Set.of(
                        WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.RETRYABLE,
                        WorkflowNodeCapability.MOCKABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return new WorkflowNodeHandler() {
            @Override
            public WorkflowNodeDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public WorkflowNodeResult execute(WorkflowNodeContext context) {
                context.cancellation().throwIfCancellationRequested();
                return WorkflowNodeResult.success(context.input());
            }
        };
    }

    private ObjectNode emptyConfigSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode joinConfigSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putArray("required").add("mode");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("mode").put("type", "string")
                .putArray("enum").add("ANY").add("ALL").add("N_OF_M");
        properties.putObject("requiredBranches").put("type", "integer")
                .put("minimum", 1).put("maximum", 100);
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode loopConfigSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putArray("required").add("maxIterations");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("version").put("type", "integer")
                .put("minimum", 1).put("maximum", 2);
        properties.putObject("mode").put("type", "string")
                .putArray("enum").add("FOR_EACH").add("REPEAT");
        properties.putObject("repeatMode").put("type", "string")
                .putArray("enum").add("COUNT").add("UNTIL");
        properties.putObject("count").put("type", "integer")
                .put("minimum", 1).put("maximum", 1000);
        properties.putObject("stopCondition").put("type", "string")
                .put("maxLength", 2000);
        properties.putObject("conditionField").put("type", "string")
                .put("maxLength", 500);
        properties.putObject("conditionOperator").put("type", "string")
                .putArray("enum").add("==").add("!=").add(">").add(">=").add("<").add("<=");
        properties.putObject("conditionValueType").put("type", "string")
                .putArray("enum").add("string").add("number").add("boolean").add("null");
        properties.putObject("conditionValue");
        properties.putObject("checkBeforeFirst").put("type", "boolean");
        properties.putObject("maxIterations").put("type", "integer")
                .put("minimum", 1).put("maximum", 1000);
        properties.putObject("resultMode").put("type", "string")
                .putArray("enum").add("COLLECT").add("LAST").add("NONE");
        properties.putObject("resultNodeId").put("type", "string")
                .put("maxLength", 128);
        properties.putObject("maxResults").put("type", "integer")
                .put("minimum", 1).put("maximum", 1000);
        properties.putObject("itemErrorPolicy").put("type", "string")
                .putArray("enum").add("FAIL").add("SKIP").add("COLLECT");
        properties.putObject("emptyPolicy").put("type", "string")
                .putArray("enum").add("COMPLETE").add("FAIL");
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode loopOutputSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("completed").put("type", "boolean");
        properties.putObject("iterations").put("type", "integer");
        properties.putObject("successCount").put("type", "integer");
        properties.putObject("failureCount").put("type", "integer");
        properties.putObject("stopReason").put("type", "string");
        properties.putObject("lastResult");
        properties.putObject("results").put("type", "array");
        properties.putObject("errors").put("type", "array");
        schema.put("additionalProperties", true);
        return schema;
    }

    private WorkflowNodeHandler markerHandler(WorkflowNodeDescriptor descriptor) {
        return new WorkflowNodeHandler() {
            @Override
            public WorkflowNodeDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public WorkflowNodeResult execute(WorkflowNodeContext context) {
                throw new IllegalStateException("持久化控制节点必须由工作流执行引擎调度");
            }
        };
    }

    private ObjectNode approvalConfigSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("configVersion").put("type", "string")
                .putArray("enum").add("2.0");
        ObjectNode content = properties.putObject("content");
        content.put("type", "object");
        ObjectNode contentProperties = content.putObject("properties");
        contentProperties.putObject("titleTemplate").put("type", "string")
                .put("minLength", 1).put("maxLength", 200);
        contentProperties.putObject("descriptionTemplate").put("type", "string")
                .put("maxLength", 2000);
        ObjectNode fields = contentProperties.putObject("fields");
        fields.put("type", "array").put("maxItems", 50);
        ObjectNode field = fields.putObject("items");
        field.put("type", "object").put("additionalProperties", false);
        ObjectNode fieldProperties = field.putObject("properties");
        fieldProperties.putObject("key").put("type", "string")
                .put("minLength", 1).put("maxLength", 64);
        fieldProperties.putObject("label").put("type", "string")
                .put("minLength", 1).put("maxLength", 128);
        ObjectNode source = fieldProperties.putObject("source");
        source.put("type", "object").put("additionalProperties", false);
        ObjectNode sourceProperties = source.putObject("properties");
        sourceProperties.putObject("expression").put("type", "string")
                .put("minLength", 1).put("maxLength", 2000);
        sourceProperties.set("value", JsonNodeFactory.instance.objectNode());
        fieldProperties.putObject("displayType").put("type", "string")
                .putArray("enum").add("AUTO").add("TEXT").add("NUMBER")
                .add("MONEY").add("DATE").add("DATETIME").add("BOOLEAN")
                .add("OBJECT").add("ARRAY").add("LINK").add("ATTACHMENT").add("JSON");
        fieldProperties.putObject("mask").put("type", "string")
                .putArray("enum").add("NONE").add("PARTIAL").add("HIDDEN")
                .add("PHONE").add("EMAIL").add("ID_CARD").add("CUSTOM");
        fieldProperties.putObject("maskPattern").put("type", "string").put("maxLength", 128);
        field.putArray("required").add("key").add("label").add("source")
                .add("displayType").add("mask");
        content.putArray("required").add("titleTemplate").add("fields");
        content.put("additionalProperties", false);

        ObjectNode stages = properties.putObject("stages");
        stages.put("type", "array").put("minItems", 1).put("maxItems", 20);
        ObjectNode stage = stages.putObject("items");
        stage.put("type", "object").put("additionalProperties", false);
        ObjectNode stageProperties = stage.putObject("properties");
        stageProperties.putObject("id").put("type", "string")
                .put("minLength", 1).put("maxLength", 64);
        stageProperties.putObject("name").put("type", "string")
                .put("minLength", 1).put("maxLength", 128);
        ObjectNode targets = stageProperties.putObject("targets");
        targets.put("type", "array").put("minItems", 1).put("maxItems", 20);
        ObjectNode target = targets.putObject("items");
        target.put("type", "object").put("additionalProperties", false);
        ObjectNode targetProperties = target.putObject("properties");
        targetProperties.putObject("type").put("type", "string")
                .putArray("enum").add("USER").add("ROLE").add("DEPARTMENT");
        ObjectNode targetIds = targetProperties.putObject("ids");
        targetIds.put("type", "array").put("minItems", 1)
                .put("maxItems", 500).put("uniqueItems", true);
        targetIds.putObject("items").put("type", "string")
                .put("minLength", 1).put("maxLength", 128);
        targetProperties.putObject("includeChildren").put("type", "boolean");
        target.putArray("required").add("type").add("ids");
        ObjectNode policy = stageProperties.putObject("decisionPolicy");
        policy.put("type", "object").put("additionalProperties", false);
        ObjectNode policyProperties = policy.putObject("properties");
        policyProperties.putObject("mode").put("type", "string")
                .putArray("enum").add("ANY").add("ALL").add("N_OF_M");
        policyProperties.putObject("requiredApprovals").put("type", "integer")
                .put("minimum", 1).put("maximum", 500);
        policyProperties.putObject("rejectOnAny").put("type", "boolean");
        policy.putArray("required").add("mode").add("requiredApprovals").add("rejectOnAny");
        stageProperties.set("deadline", approvalDeadlineSchema(true));
        stageProperties.set("fallbackTargets", approvalTargetsSchema(false));
        stage.putArray("required").add("id").add("name").add("targets")
                .add("decisionPolicy").add("deadline");

        ObjectNode resultPolicy = properties.putObject("resultPolicy");
        resultPolicy.put("type", "object").put("additionalProperties", false);
        ObjectNode resultProperties = resultPolicy.putObject("properties");
        resultProperties.putObject("mode").put("type", "string")
                .putArray("enum").add("SIMPLE").add("BRANCH");
        resultProperties.putObject("rejectAction").put("type", "string")
                .putArray("enum").add("END").add("BRANCH");
        resultProperties.putObject("expireAction").put("type", "string")
                .putArray("enum").add("END").add("BRANCH");
        resultPolicy.putArray("required").add("mode").add("rejectAction").add("expireAction");
        properties.set("deadline", approvalDeadlineSchema(false));
        ObjectNode reminder = properties.putObject("reminder");
        reminder.put("type", "object").put("additionalProperties", false);
        ObjectNode reminderProperties = reminder.putObject("properties");
        reminderProperties.putObject("enabled").put("type", "boolean");
        reminderProperties.putObject("beforeDuration").put("type", "integer")
                .put("minimum", 1).put("maximum", 525600);
        reminderProperties.putObject("beforeUnit").put("type", "string")
                .putArray("enum").add("MINUTE").add("HOUR").add("DAY");
        reminder.putArray("required").add("enabled").add("beforeDuration").add("beforeUnit");
        ObjectNode expiration = properties.putObject("expirationPolicy");
        expiration.put("type", "object").put("additionalProperties", false);
        ObjectNode expirationProperties = expiration.putObject("properties");
        expirationProperties.putObject("action").put("type", "string")
                .putArray("enum").add("EXPIRE").add("REASSIGN");
        expirationProperties.set("targets", approvalTargetsSchema(false));
        expirationProperties.putObject("maxEscalations").put("type", "integer")
                .put("minimum", 0).put("maximum", 10);
        expiration.putArray("required").add("action").add("targets").add("maxEscalations");
        ObjectNode options = properties.putObject("options");
        options.put("type", "object").put("additionalProperties", false);
        ObjectNode optionProperties = options.putObject("properties");
        optionProperties.putObject("allowSelfApproval").put("type", "boolean");
        optionProperties.putObject("requireApproveComment").put("type", "boolean");
        optionProperties.putObject("requireRejectComment").put("type", "boolean");
        options.putArray("required").add("allowSelfApproval")
                .add("requireApproveComment").add("requireRejectComment");
        schema.putArray("required").add("configVersion").add("content")
                .add("stages").add("resultPolicy").add("deadline").add("options");
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode approvalDeadlineSchema(boolean nullable) {
        ObjectNode deadline = JsonNodeFactory.instance.objectNode();
        if (nullable) deadline.putArray("type").add("object").add("null");
        else deadline.put("type", "object");
        ObjectNode properties = deadline.putObject("properties");
        properties.putObject("duration").put("type", "integer")
                .put("minimum", 1).put("maximum", 525600);
        properties.putObject("unit").put("type", "string")
                .putArray("enum").add("MINUTE").add("HOUR").add("DAY");
        properties.putObject("calendar").put("type", "string")
                .putArray("enum").add("CALENDAR_DAY").add("BUSINESS_DAY");
        properties.putObject("timezone").put("type", "string")
                .put("minLength", 1).put("maxLength", 64);
        deadline.putArray("required").add("duration").add("unit")
                .add("calendar").add("timezone");
        deadline.put("additionalProperties", false);
        return deadline;
    }

    private ObjectNode approvalTargetsSchema(boolean required) {
        ObjectNode targets = JsonNodeFactory.instance.objectNode();
        targets.put("type", "array").put("maxItems", 20);
        if (required) targets.put("minItems", 1);
        ObjectNode target = targets.putObject("items");
        target.put("type", "object").put("additionalProperties", false);
        ObjectNode properties = target.putObject("properties");
        properties.putObject("type").put("type", "string")
                .putArray("enum").add("USER").add("ROLE").add("DEPARTMENT");
        ObjectNode ids = properties.putObject("ids");
        ids.put("type", "array").put("minItems", 1).put("maxItems", 500)
                .put("uniqueItems", true);
        ids.putObject("items").put("type", "string").put("minLength", 1)
                .put("maxLength", 128);
        properties.putObject("includeChildren").put("type", "boolean");
        target.putArray("required").add("type").add("ids");
        return targets;
    }

    private ObjectNode approvalOutputSchema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("status").put("type", "string")
                .putArray("enum").add("APPROVED").add("REJECTED").add("EXPIRED");
        properties.putObject("approvalInstanceId").put("type", "string");
        properties.putObject("approvedCount").put("type", "integer");
        properties.putObject("rejectedCount").put("type", "integer");
        properties.putObject("stageCount").put("type", "integer");
        properties.putObject("startedAt").put("type", "integer");
        properties.putObject("completedStageCount").put("type", "integer");
        properties.putObject("totalStageCount").put("type", "integer");
        ObjectNode summary = properties.putObject("decisionSummary");
        summary.put("type", "object");
        ObjectNode summaryProperties = summary.putObject("properties");
        summaryProperties.putObject("approvedCount").put("type", "integer");
        summaryProperties.putObject("rejectedCount").put("type", "integer");
        summaryProperties.putObject("completedStageCount").put("type", "integer");
        summaryProperties.putObject("totalStageCount").put("type", "integer");
        summary.putArray("required").add("approvedCount").add("rejectedCount")
                .add("completedStageCount").add("totalStageCount");
        summary.put("additionalProperties", false);
        properties.putObject("finishedAt").put("type", "integer");
        properties.putObject("finalActor").put("type", "string");
        properties.putObject("simulated").put("type", "boolean");
        schema.putArray("required")
                .add("status").add("approvalInstanceId").add("approvedCount")
                .add("rejectedCount").add("stageCount").add("startedAt")
                .add("completedStageCount").add("totalStageCount")
                .add("decisionSummary").add("finishedAt");
        return schema;
    }

    private ObjectNode subWorkflowOutputSchema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("result");
        properties.putObject("execution").put("type", "object");
        schema.putArray("required").add("result").add("execution");
        return schema;
    }

    private ObjectNode objectSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        schema.put("additionalProperties", false);
        return schema;
    }

    private record TransformPathToken(String field, boolean each) {
    }
}
