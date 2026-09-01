package com.polaris.ai.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/** 数据转换节点使用的无函数、无反射受限表达式。 */
final class WorkflowTransformExpressionEvaluator {

    private static final int MAX_LENGTH = 2000;

    private WorkflowTransformExpressionEvaluator() {
    }

    static JsonNode evaluate(String expression, JsonNode value, JsonNode source, JsonNode sources) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("受限表达式不能为空");
        }
        if (expression.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("受限表达式不能超过 " + MAX_LENGTH + " 个字符");
        }
        Parser parser = new Parser(expression, value, source, sources);
        JsonNode result = parser.parseOr();
        parser.skipWhitespace();
        if (!parser.end()) {
            throw parser.error("包含无法识别的内容");
        }
        return result == null ? NullNode.instance : result;
    }

    private static final class Parser {
        private final String expression;
        private final JsonNode value;
        private final JsonNode source;
        private final JsonNode sources;
        private int index;
        private int depth;

        private Parser(String expression, JsonNode value, JsonNode source, JsonNode sources) {
            this.expression = expression;
            this.value = value;
            this.source = source;
            this.sources = sources;
        }

        private JsonNode parseOr() {
            JsonNode result = parseAnd();
            while (match("||")) {
                result = BooleanNode.valueOf(booleanValue(result) | booleanValue(parseAnd()));
            }
            return result;
        }

        private JsonNode parseAnd() {
            JsonNode result = parseEquality();
            while (match("&&")) {
                result = BooleanNode.valueOf(booleanValue(result) & booleanValue(parseEquality()));
            }
            return result;
        }

        private JsonNode parseEquality() {
            JsonNode result = parseComparison();
            while (true) {
                if (match("==")) {
                    result = BooleanNode.valueOf(equal(result, parseComparison()));
                } else if (match("!=")) {
                    result = BooleanNode.valueOf(!equal(result, parseComparison()));
                } else {
                    return result;
                }
            }
        }

        private JsonNode parseComparison() {
            JsonNode result = parseAdditive();
            while (true) {
                if (match(">=")) result = BooleanNode.valueOf(compare(result, parseAdditive()) >= 0);
                else if (match("<=")) result = BooleanNode.valueOf(compare(result, parseAdditive()) <= 0);
                else if (match(">")) result = BooleanNode.valueOf(compare(result, parseAdditive()) > 0);
                else if (match("<")) result = BooleanNode.valueOf(compare(result, parseAdditive()) < 0);
                else return result;
            }
        }

        private JsonNode parseAdditive() {
            JsonNode result = parseMultiplicative();
            while (true) {
                if (match("+")) {
                    JsonNode right = parseMultiplicative();
                    if (result.isTextual() || right.isTextual()) {
                        result = JsonNodeFactory.instance.textNode(text(result) + text(right));
                    } else {
                        result = number(decimal(result).add(decimal(right)));
                    }
                } else if (match("-")) {
                    result = number(decimal(result).subtract(decimal(parseMultiplicative())));
                } else {
                    return result;
                }
            }
        }

        private JsonNode parseMultiplicative() {
            JsonNode result = parseUnary();
            while (true) {
                if (match("*")) {
                    result = number(decimal(result).multiply(decimal(parseUnary())));
                } else if (match("/")) {
                    BigDecimal divisor = decimal(parseUnary());
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) throw error("不能除以零");
                    result = number(decimal(result).divide(divisor, MathContext.DECIMAL128));
                } else if (match("%")) {
                    BigDecimal divisor = decimal(parseUnary());
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) throw error("不能除以零");
                    result = number(decimal(result).remainder(divisor));
                } else {
                    return result;
                }
            }
        }

        private JsonNode parseUnary() {
            if (match("!")) return BooleanNode.valueOf(!booleanValue(parseUnary()));
            if (match("-")) return number(decimal(parseUnary()).negate());
            return parsePrimary();
        }

        private JsonNode parsePrimary() {
            skipWhitespace();
            if (++depth > 64) throw error("嵌套层级过深");
            try {
                if (match("(")) {
                    JsonNode result = parseOr();
                    if (!match(")")) throw error("缺少右括号");
                    return result;
                }
                if (peek('$')) return resolvePath(readPath());
                if (peek('\'') || peek('"')) return JsonNodeFactory.instance.textNode(readString());
                if (peekDigit() || peek('-') && nextIsDigit()) return readNumber();
                if (matchWord("true")) return BooleanNode.TRUE;
                if (matchWord("false")) return BooleanNode.FALSE;
                if (matchWord("null")) return NullNode.instance;
                throw error("需要变量、数值、文本或括号表达式");
            } finally {
                depth--;
            }
        }

        private JsonNode resolvePath(String path) {
            String[] parts = path.substring(1).split("\\.");
            JsonNode current;
            int cursor;
            switch (parts[0]) {
                case "value" -> {
                    current = value;
                    cursor = 1;
                }
                case "source", "item" -> {
                    current = source;
                    cursor = 1;
                }
                case "sources" -> {
                    current = sources;
                    cursor = 1;
                }
                default -> throw error("变量只允许 $value、$source、$item 或 $sources");
            }
            for (; cursor < parts.length; cursor++) {
                String part = parts[cursor];
                if (current == null || current.isNull() || current.isMissingNode()) {
                    throw error("变量路径不存在: " + path);
                }
                if (current.isArray() && part.chars().allMatch(Character::isDigit)) {
                    current = current.path(Integer.parseInt(part));
                } else {
                    current = current.path(part);
                }
            }
            if (current == null || current.isMissingNode()) {
                throw error("变量路径不存在: " + path);
            }
            return current.deepCopy();
        }

        private String readPath() {
            int start = index++;
            while (!end()) {
                char ch = expression.charAt(index);
                if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.') index++;
                else break;
            }
            String path = expression.substring(start, index);
            if (!path.matches("\\$(value|source|item|sources)(?:\\.[\\p{L}\\p{N}_-]+)*")) {
                throw error("变量路径格式无效");
            }
            return path;
        }

        private String readString() {
            char quote = expression.charAt(index++);
            StringBuilder result = new StringBuilder();
            while (!end()) {
                char ch = expression.charAt(index++);
                if (ch == quote) return result.toString();
                if (ch != '\\') {
                    result.append(ch);
                    continue;
                }
                if (end()) throw error("文本转义不完整");
                char escaped = expression.charAt(index++);
                result.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '\\', '\'', '"' -> escaped;
                    default -> throw error("不支持的转义字符");
                });
            }
            throw error("文本没有闭合");
        }

        private JsonNode readNumber() {
            int start = index;
            if (peek('-')) index++;
            while (peekDigit()) index++;
            if (peek('.')) {
                index++;
                while (peekDigit()) index++;
            }
            try {
                return number(new BigDecimal(expression.substring(start, index)));
            } catch (NumberFormatException exception) {
                throw error("数字格式无效");
            }
        }

        private boolean match(String token) {
            skipWhitespace();
            if (!expression.startsWith(token, index)) return false;
            index += token.length();
            return true;
        }

        private boolean matchWord(String word) {
            skipWhitespace();
            if (!expression.startsWith(word, index)) return false;
            int end = index + word.length();
            if (end < expression.length() && Character.isLetterOrDigit(expression.charAt(end))) return false;
            index = end;
            return true;
        }

        private void skipWhitespace() {
            while (!end() && Character.isWhitespace(expression.charAt(index))) index++;
        }

        private boolean end() {
            return index >= expression.length();
        }

        private boolean peek(char expected) {
            return !end() && expression.charAt(index) == expected;
        }

        private boolean peekDigit() {
            return !end() && Character.isDigit(expression.charAt(index));
        }

        private boolean nextIsDigit() {
            return index + 1 < expression.length() && Character.isDigit(expression.charAt(index + 1));
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + "，位置 " + index);
        }
    }

    private static boolean booleanValue(JsonNode value) {
        if (value != null && value.isBoolean()) return value.booleanValue();
        throw new IllegalArgumentException("逻辑运算只接受是/否值");
    }

    private static BigDecimal decimal(JsonNode value) {
        if (value != null && value.isNumber()) return value.decimalValue();
        if (value != null && value.isTextual()) {
            try {
                return new BigDecimal(value.asText());
            } catch (NumberFormatException ignored) {
                // 统一使用下面的字段级错误。
            }
        }
        throw new IllegalArgumentException("算术运算只接受数字");
    }

    private static JsonNode number(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() <= 0) {
            try {
                return JsonNodeFactory.instance.numberNode(normalized.longValueExact());
            } catch (ArithmeticException ignored) {
                // 超出 long 时保留任意精度数字。
            }
        }
        return JsonNodeFactory.instance.numberNode(normalized);
    }

    private static String text(JsonNode value) {
        if (value == null || value.isNull()) return "";
        return value.isContainerNode() ? value.toString() : value.asText();
    }

    private static boolean equal(JsonNode left, JsonNode right) {
        if (left != null && right != null && left.isNumber() && right.isNumber()) {
            return left.decimalValue().compareTo(right.decimalValue()) == 0;
        }
        return Objects.equals(left, right);
    }

    private static int compare(JsonNode left, JsonNode right) {
        if (left != null && right != null && left.isNumber() && right.isNumber()) {
            return left.decimalValue().compareTo(right.decimalValue());
        }
        if (left != null && right != null && left.isTextual() && right.isTextual()) {
            return left.asText().compareTo(right.asText());
        }
        throw new IllegalArgumentException("大小比较只支持数字或文本");
    }
}
