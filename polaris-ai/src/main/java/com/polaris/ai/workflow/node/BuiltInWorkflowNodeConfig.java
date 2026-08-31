package com.polaris.ai.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.polaris.ai.workflow.application.WorkflowArtifactView;
import com.polaris.ai.workflow.service.WorkflowArtifactService;
import com.polaris.ai.workflow.spi.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** 确定性内置节点配置；依赖连接器的节点单独注册。 */
@Configuration
public class BuiltInWorkflowNodeConfig {

    private static final String TRANSFORM_TARGET_PATH_PATTERN =
            "^[A-Za-z_][A-Za-z0-9_]*(?:\\[\\])*(?:\\.[A-Za-z_][A-Za-z0-9_]*(?:\\[\\])*){0,9}$";
    private static final String TRANSFORM_SOURCE_PATH_PATTERN =
            "^$|^(?:[A-Za-z_][A-Za-z0-9_-]*|\\[\\])(?:\\[\\])*(?:\\.(?:[A-Za-z_][A-Za-z0-9_-]*|[0-9]+)(?:\\[\\])*)*$";

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
                JsonNode config = context.config() == null
                        ? JsonNodeFactory.instance.objectNode() : context.config();
                if (!config.has("mode") && !config.has("rules")) {
                    return WorkflowNodeResult.success(source.deepCopy());
                }
                String mode = config.path("mode").asText("OBJECT_MAP");
                JsonNode rules = config.path("rules");
                validateTransformRules(rules);
                boolean preserveUnmapped = config.path("preserveUnmapped").asBoolean(false);
                int maximum = config.path("maxItems").asInt(1000);
                if ("ARRAY_MAP".equals(mode)) {
                    if (!source.isArray()) {
                        throw new IllegalArgumentException("数组逐项整理要求数据来源是数组");
                    }
                    if (source.size() > maximum) {
                        throw new IllegalArgumentException(
                                "数组元素数量超过当前节点上限 " + maximum);
                    }
                    ArrayNode output = JsonNodeFactory.instance.arrayNode();
                    for (int index = 0; index < source.size(); index++) {
                        context.cancellation().throwIfCancellationRequested();
                        output.add(transformObject(
                                source.get(index), rules, preserveUnmapped, index, maximum));
                    }
                    return WorkflowNodeResult.success(output);
                }
                if (!"OBJECT_MAP".equals(mode)) {
                    throw new IllegalArgumentException("不支持的数据转换模式: " + mode);
                }
                return WorkflowNodeResult.success(
                        transformObject(source, rules, preserveUnmapped, null, maximum));
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
                .putArray("enum").add("OBJECT_MAP").add("ARRAY_MAP");
        properties.putObject("preserveUnmapped").put("type", "boolean")
                .put("title", "保留未配置字段").put("default", false);
        properties.putObject("maxItems").put("type", "integer")
                .put("title", "数组最大处理条数")
                .put("minimum", 1).put("maximum", 10000).put("default", 1000);

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
                .add("ARRAY_JOIN").add("ARRAY_LENGTH");
        ruleProperties.putObject("resultType").put("type", "string")
                .put("title", "输出类型").putArray("enum")
                .add("object").add("array").add("string").add("integer")
                .add("number").add("boolean").add("null");
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
            JsonNode source, JsonNode rules, boolean preserveUnmapped,
            Integer arrayIndex, int maximumArrayItems) {
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
            List<TransformPathToken> sourceTokens = parseTransformPath(
                    rule.path("sourcePath").asText(""));
            walkTransformSource(source, sourceTokens, 0, new ArrayList<>(), 0,
                    output, targetTokens, rule, arrayIndex, maximumArrayItems);
        }
        return output;
    }

    private void walkTransformSource(
            JsonNode current, List<TransformPathToken> sourceTokens, int tokenIndex,
            List<Integer> indexes, int wildcardDepth, ObjectNode output,
            List<TransformPathToken> targetTokens, JsonNode rule, Integer outerArrayIndex,
            int maximumArrayItems) {
        if (tokenIndex >= sourceTokens.size()) {
            applyTransformLeaf(output, targetTokens, indexes, rule,
                    current == null ? MissingNode.getInstance() : current, outerArrayIndex);
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
                        outerArrayIndex, maximumArrayItems);
                indexes.remove(indexes.size() - 1);
            }
            return;
        }
        JsonNode next = readTransformField(current, token.field());
        walkTransformSource(next, sourceTokens, tokenIndex + 1, indexes,
                wildcardDepth, output, targetTokens, rule, outerArrayIndex,
                maximumArrayItems);
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
            JsonNode rule, JsonNode original, Integer outerArrayIndex) {
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
                    rule.path("operation").asText("COPY"), value, rule);
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

    private JsonNode applyOperation(String operation, JsonNode value, JsonNode rule) {
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
            default -> throw new IllegalArgumentException("不支持的处理方式: " + operation);
        };
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
        if (path == null || !path.matches(TRANSFORM_TARGET_PATH_PATTERN)) {
            throw new IllegalArgumentException("输出字段路径无效: " + path);
        }
    }

    private void validateSourcePath(String path) {
        if (path == null || !path.matches(TRANSFORM_SOURCE_PATH_PATTERN)) {
            throw new IllegalArgumentException("来源字段路径无效: " + path);
        }
    }

    private void validateTransformRules(JsonNode rules) {
        if (rules == null || !rules.isArray()) return;
        List<String> paths = new ArrayList<>();
        for (JsonNode rule : rules) {
            String path = rule.path("targetPath").asText("").trim();
            validateTargetPath(path);
            String operation = rule.path("operation").asText("COPY");
            String sourcePath = rule.path("sourcePath").asText("");
            if (!"CONSTANT".equals(operation)) {
                validateSourcePath(sourcePath);
                int sourceWildcards = countWildcards(parseTransformPath(sourcePath));
                int targetWildcards = countWildcards(parseTransformPath(path));
                if (sourceWildcards != targetWildcards) {
                    throw new IllegalArgumentException(
                            "来源和输出路径的数组层级必须一致: " + sourcePath + " → " + path);
                }
            } else if (path.contains("[]")) {
                throw new IllegalArgumentException("固定值不能直接写入数组通配路径: " + path);
            }
            for (String existing : paths) {
                if (transformPathsConflict(existing, path)) {
                    throw new IllegalArgumentException(
                            "输出字段路径冲突: " + existing + " 与 " + path);
                }
            }
            paths.add(path);
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
            case "TO_STRING", "TRIM", "UPPERCASE", "LOWERCASE", "ARRAY_JOIN" -> "string";
            case "TO_INTEGER", "ARRAY_LENGTH" -> "integer";
            case "TO_NUMBER" -> "number";
            case "TO_BOOLEAN" -> "boolean";
            case "CONSTANT" -> rule.hasNonNull("resultType")
                    ? rule.path("resultType").asText() : jsonType(rule.get("value"));
            default -> rule.path("resultType").asText("object");
        };
    }

    private ObjectNode operationResultSchema(JsonNode rule) {
        String operation = rule.path("operation").asText("COPY");
        if ("COPY".equals(operation) && rule.path("resultSchema").isObject()
                && rule.path("resultSchema").has("type")) {
            return ((ObjectNode) rule.path("resultSchema")).deepCopy();
        }
        if ("CONSTANT".equals(operation) && rule.has("value")) {
            return inferTransformValueSchema(rule.get("value"), 0);
        }
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        String type = operationResultType(rule);
        schema.put("type", type);
        if ("object".equals(type)) schema.put("additionalProperties", true);
        if ("array".equals(type)) schema.putObject("items");
        return schema;
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
        String type = resultSchema.path("type").asText("object");
        ensureSchemaType(schema, type, path);
        schema.removeAll();
        schema.setAll(resultSchema.deepCopy());
        if (!schema.has("type")) schema.put("type", type);
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
        return passThrough("loop", "受控循环", "control", loopConfigSchema());
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
    public WorkflowNodeHandler waitWorkflowNodeHandler() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putArray("required").add("delaySeconds");
        ObjectNode delay = schema.putObject("properties").putObject("delaySeconds");
        delay.put("type", "integer");
        delay.put("minimum", 1);
        delay.put("maximum", 604800);
        delay.put("description", "持久化等待秒数，不占用Worker线程");
        schema.put("additionalProperties", false);
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "wait", "1.0", "定时等待", "control", schema,
                JsonNodeFactory.instance.objectNode(), waitOutputSchema(),
                WorkflowSideEffect.NONE, Set.of(),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.MOCKABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return markerHandler(descriptor);
    }

    @Bean
    public WorkflowNodeHandler artifactWorkflowNodeHandler(
            WorkflowArtifactService artifactService) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("fileName").put("type", "string")
                .put("minLength", 1).put("maxLength", 200);
        properties.putObject("mimeType").put("type", "string")
                .put("pattern", "^[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+$");
        properties.putObject("retentionDays").put("type", "integer")
                .put("minimum", 1).put("maximum", 3650);
        schema.put("additionalProperties", false);
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "artifact", "1.0", "保存产物", "data", schema,
                JsonNodeFactory.instance.objectNode(), artifactOutputSchema(),
                WorkflowSideEffect.NONE, Set.of(),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.RETRYABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return new WorkflowNodeHandler() {
            @Override
            public WorkflowNodeDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public WorkflowNodeResult execute(WorkflowNodeContext context) {
                context.cancellation().throwIfCancellationRequested();
                WorkflowArtifactView artifact = artifactService.storeJson(
                        context.tenantId(), context.executionId(), context.nodeRunId(),
                        context.input(), context.config().path("fileName").asText(null),
                        context.config().path("mimeType").asText("application/json"),
                        context.config().path("retentionDays").asInt(30));
                ObjectNode output = JsonNodeFactory.instance.objectNode();
                output.put("artifactId", artifact.artifactId());
                output.put("fileName", artifact.fileName());
                output.put("mimeType", artifact.mimeType());
                output.put("sizeBytes", artifact.sizeBytes());
                output.put("contentHash", artifact.contentHash());
                return WorkflowNodeResult.success(output);
            }
        };
    }

    @Bean
    public WorkflowNodeHandler subWorkflowNodeHandler() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putArray("required").add("workflowCode").add("workflowVersionId");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("workflowCode").put("type", "string")
                .put("pattern", "^[A-Za-z][A-Za-z0-9_.-]{0,63}$");
        properties.putObject("workflowVersionId").put("type", "string")
                .put("minLength", 1).put("maxLength", 64);
        properties.putObject("pollSeconds").put("type", "integer")
                .put("minimum", 1).put("maximum", 60);
        schema.put("additionalProperties", false);
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "sub_workflow", "1.0", "子工作流", "control", schema,
                JsonNodeFactory.instance.objectNode(), subWorkflowOutputSchema(),
                WorkflowSideEffect.NONE, Set.of(),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return markerHandler(descriptor);
    }

    private WorkflowNodeHandler passThrough(
            String type, String name, String category, ObjectNode configSchema) {
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                type,
                "1.0",
                name,
                category,
                configSchema,
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(),
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
        schema.putObject("properties").putObject("maxIterations")
                .put("type", "integer").put("minimum", 1).put("maximum", 1000);
        schema.put("additionalProperties", false);
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
        schema.putArray("required")
                .add("assigneeType").add("assigneeIds").add("approvalMode")
                .add("timeoutSeconds");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("assigneeType").put("type", "string")
                .putArray("enum").add("USER").add("ROLE").add("DEPARTMENT");
        ObjectNode ids = properties.putObject("assigneeIds");
        ids.put("type", "array");
        ids.put("minItems", 1);
        ids.put("uniqueItems", true);
        ids.putObject("items").put("type", "string").put("minLength", 1).put("maxLength", 128);
        properties.putObject("approvalMode").put("type", "string")
                .putArray("enum").add("ANY").add("ALL").add("SEQUENTIAL").add("N_OF_M");
        properties.putObject("requiredApprovals").put("type", "integer")
                .put("minimum", 1).put("maximum", 100);
        properties.putObject("allowSelfApproval").put("type", "boolean");
        properties.putObject("timeoutSeconds").put("type", "integer")
                .put("minimum", 60).put("maximum", 604800);
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode approvalOutputSchema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("status").put("type", "string")
                .putArray("enum").add("APPROVED");
        properties.putObject("approvalTaskId").put("type", "string");
        properties.putObject("finishedAt").put("type", "integer");
        schema.putArray("required")
                .add("status").add("approvalTaskId").add("finishedAt");
        return schema;
    }

    private ObjectNode waitOutputSchema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("status").put("type", "string")
                .putArray("enum").add("RESUMED");
        properties.putObject("resumedAt").put("type", "integer");
        schema.putArray("required").add("status").add("resumedAt");
        return schema;
    }

    private ObjectNode artifactOutputSchema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("artifactId").put("type", "string");
        properties.putObject("fileName").put("type", "string");
        properties.putObject("mimeType").put("type", "string");
        properties.putObject("sizeBytes").put("type", "integer");
        properties.putObject("contentHash").put("type", "string");
        schema.putArray("required")
                .add("artifactId").add("fileName").add("mimeType")
                .add("sizeBytes").add("contentHash");
        return schema;
    }

    private ObjectNode subWorkflowOutputSchema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("childExecutionId").put("type", "string");
        properties.putObject("status").put("type", "string")
                .putArray("enum").add("SUCCEEDED");
        properties.set("output", JsonNodeFactory.instance.objectNode());
        schema.putArray("required").add("childExecutionId").add("status").add("output");
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
