package com.polaris.ai.workflow.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.polaris.ai.workflow.definition.WorkflowExecutionPlan;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/** 仅计算由编译器生成的工作流表达式抽象语法树节点。 */
@Component
public class WorkflowExpressionEvaluator {

    private final ObjectMapper objectMapper;

    public WorkflowExpressionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean evaluateCondition(JsonNode ast, JsonNode context) {
        JsonNode value = evaluate(ast, context);
        if (!value.isBoolean()) {
            throw new IllegalArgumentException("条件表达式结果必须为布尔值");
        }
        return value.booleanValue();
    }

    public JsonNode evaluate(JsonNode ast, JsonNode context) {
        if (ast == null || !ast.isObject()) {
            throw new IllegalArgumentException("表达式AST无效");
        }
        String type = ast.path("type").asText();
        return switch (type) {
            case "literal" -> ast.has("value") ? ast.get("value") : NullNode.instance;
            case "path" -> resolvePath(context, ast.path("value").asText());
            case "unary" -> evaluateUnary(ast, context);
            case "binary" -> evaluateBinary(ast, context);
            default -> throw new IllegalArgumentException("不支持的表达式AST类型: " + type);
        };
    }

    public ObjectNode evaluateBindings(
            Map<String, WorkflowExecutionPlan.PlanValueBinding> bindings,
            JsonNode context) {
        ObjectNode result = objectMapper.createObjectNode();
        if (bindings == null) {
            return result;
        }
        bindings.forEach((key, binding) -> {
            JsonNode ast = binding.getExpressionAst();
            JsonNode value = ast == null ? binding.getValue() : evaluate(ast, context);
            result.set(key, value == null ? NullNode.instance : value.deepCopy());
        });
        return result;
    }

    private JsonNode evaluateUnary(JsonNode ast, JsonNode context) {
        if (!"NOT".equals(ast.path("operator").asText())) {
            throw new IllegalArgumentException("不支持的一元运算符");
        }
        JsonNode operand = evaluate(ast.get("operand"), context);
        if (!operand.isBoolean()) {
            throw new IllegalArgumentException("NOT运算只接受布尔值");
        }
        return BooleanNode.valueOf(!operand.booleanValue());
    }

    private JsonNode evaluateBinary(JsonNode ast, JsonNode context) {
        String operator = ast.path("operator").asText();
        if ("AND".equals(operator) || "OR".equals(operator)) {
            JsonNode left = evaluate(ast.get("left"), context);
            if (!left.isBoolean()) {
                throw new IllegalArgumentException("逻辑运算只接受布尔值");
            }
            if ("AND".equals(operator) && !left.booleanValue()) {
                return BooleanNode.FALSE;
            }
            if ("OR".equals(operator) && left.booleanValue()) {
                return BooleanNode.TRUE;
            }
            JsonNode right = evaluate(ast.get("right"), context);
            if (!right.isBoolean()) {
                throw new IllegalArgumentException("逻辑运算只接受布尔值");
            }
            return BooleanNode.valueOf(right.booleanValue());
        }
        JsonNode left = evaluate(ast.get("left"), context);
        JsonNode right = evaluate(ast.get("right"), context);
        return switch (operator) {
            case "EQ" -> BooleanNode.valueOf(equalValues(left, right));
            case "NE" -> BooleanNode.valueOf(!equalValues(left, right));
            case "GT" -> BooleanNode.valueOf(compare(left, right) > 0);
            case "GTE" -> BooleanNode.valueOf(compare(left, right) >= 0);
            case "LT" -> BooleanNode.valueOf(compare(left, right) < 0);
            case "LTE" -> BooleanNode.valueOf(compare(left, right) <= 0);
            default -> throw new IllegalArgumentException("不支持的二元运算符: " + operator);
        };
    }

    private JsonNode resolvePath(JsonNode context, String path) {
        if (context == null || path == null || !path.startsWith("$.")) {
            return MissingNode.getInstance();
        }
        return resolvePath(context, path.substring(2).split("\\."), 0);
    }

    private JsonNode resolvePath(JsonNode current, String[] parts, int index) {
        if (current == null || current.isMissingNode()) return MissingNode.getInstance();
        if (index >= parts.length) return current;
        String part = parts[index];
        int projections = 0;
        while (part.endsWith("[]")) {
            projections++;
            part = part.substring(0, part.length() - 2);
        }
        String field = part;
        JsonNode next = current.path(field);
        if (next.isMissingNode()) return next;
        if (projections == 0) return resolvePath(next, parts, index + 1);
        return projectArrayPath(next, projections, parts, index + 1);
    }

    private JsonNode projectArrayPath(
            JsonNode current, int projections, String[] parts, int nextIndex) {
        if (!current.isArray()) return MissingNode.getInstance();
        ArrayNode projected = objectMapper.createArrayNode();
        for (JsonNode item : current) {
            JsonNode value = projections == 1
                    ? resolvePath(item, parts, nextIndex)
                    : projectArrayPath(item, projections - 1, parts, nextIndex);
            projected.add(value.isMissingNode() ? NullNode.instance : value.deepCopy());
        }
        return projected;
    }

    private boolean equalValues(JsonNode left, JsonNode right) {
        if (left.isNumber() && right.isNumber()) {
            return decimal(left).compareTo(decimal(right)) == 0;
        }
        return left.equals(right);
    }

    private int compare(JsonNode left, JsonNode right) {
        if (left.isNumber() && right.isNumber()) {
            return decimal(left).compareTo(decimal(right));
        }
        if (left.isTextual() && right.isTextual()) {
            return left.textValue().compareTo(right.textValue());
        }
        throw new IllegalArgumentException("大小比较只支持数字或同类型字符串");
    }

    private BigDecimal decimal(JsonNode value) {
        return value.decimalValue();
    }
}
