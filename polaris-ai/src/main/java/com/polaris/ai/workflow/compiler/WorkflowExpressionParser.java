package com.polaris.ai.workflow.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 受限且确定性的工作流条件语言解析器。 */
public class WorkflowExpressionParser {

    private static final int MAX_EXPRESSION_LENGTH = 2000;

    public JsonNode parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("条件表达式不能为空");
        }
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            throw new IllegalArgumentException("条件表达式不能超过 2000 个字符");
        }
        Parser parser = new Parser(expression);
        JsonNode result = parser.parseExpression();
        parser.expect(TokenType.EOF);
        return result;
    }

    private static class Parser {
        private final Lexer lexer;
        private Token current;

        Parser(String expression) {
            this.lexer = new Lexer(expression);
            this.current = lexer.next();
        }

        JsonNode parseExpression() {
            return parseOr();
        }

        private JsonNode parseOr() {
            JsonNode left = parseAnd();
            while (match(TokenType.OR)) {
                left = binary("OR", left, parseAnd());
            }
            return left;
        }

        private JsonNode parseAnd() {
            JsonNode left = parseEquality();
            while (match(TokenType.AND)) {
                left = binary("AND", left, parseEquality());
            }
            return left;
        }

        private JsonNode parseEquality() {
            JsonNode left = parseComparison();
            while (current.type == TokenType.EQ || current.type == TokenType.NE) {
                Token operator = current;
                advance();
                left = binary(operator.type == TokenType.EQ ? "EQ" : "NE",
                        left, parseComparison());
            }
            return left;
        }

        private JsonNode parseComparison() {
            JsonNode left = parseUnary();
            while (current.type == TokenType.GT || current.type == TokenType.GTE
                    || current.type == TokenType.LT || current.type == TokenType.LTE) {
                Token operator = current;
                advance();
                left = binary(operator.type.name(), left, parseUnary());
            }
            return left;
        }

        private JsonNode parseUnary() {
            if (match(TokenType.NOT)) {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put("type", "unary");
                node.put("operator", "NOT");
                node.set("operand", parseUnary());
                return node;
            }
            return parsePrimary();
        }

        private JsonNode parsePrimary() {
            if (match(TokenType.LPAREN)) {
                JsonNode value = parseExpression();
                expect(TokenType.RPAREN);
                return value;
            }
            Token value = current;
            switch (value.type) {
                case PATH -> {
                    advance();
                    ObjectNode node = JsonNodeFactory.instance.objectNode();
                    node.put("type", "path");
                    node.put("value", value.text);
                    return node;
                }
                case STRING -> {
                    advance();
                    return literal(JsonNodeFactory.instance.textNode(value.text));
                }
                case NUMBER -> {
                    advance();
                    try {
                        return literal(JsonNodeFactory.instance.numberNode(
                                new java.math.BigDecimal(value.text)));
                    } catch (NumberFormatException e) {
                        throw error("数字格式无效");
                    }
                }
                case TRUE, FALSE -> {
                    advance();
                    return literal(JsonNodeFactory.instance.booleanNode(value.type == TokenType.TRUE));
                }
                case NULL -> {
                    advance();
                    return literal(JsonNodeFactory.instance.nullNode());
                }
                default -> throw error("缺少变量、字面量或括号表达式");
            }
        }

        private JsonNode literal(JsonNode value) {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("type", "literal");
            node.set("value", value);
            return node;
        }

        private JsonNode binary(String operator, JsonNode left, JsonNode right) {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("type", "binary");
            node.put("operator", operator);
            node.set("left", left);
            node.set("right", right);
            return node;
        }

        private boolean match(TokenType type) {
            if (current.type != type) {
                return false;
            }
            advance();
            return true;
        }

        private void expect(TokenType type) {
            if (current.type != type) {
                throw error("期望 " + type + "，实际为 " + current.type);
            }
            advance();
        }

        private void advance() {
            current = lexer.next();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + "，位置 " + current.position);
        }
    }

    private static class Lexer {
        private final String value;
        private int index;

        Lexer(String value) {
            this.value = value;
        }

        Token next() {
            skipWhitespace();
            if (index >= value.length()) {
                return new Token(TokenType.EOF, "", index);
            }
            int start = index;
            char ch = value.charAt(index);
            if (ch == '$') {
                index++;
                while (index < value.length() && isPathCharacter(value.charAt(index))) {
                    index++;
                }
                String path = value.substring(start, index);
                if (!path.matches("\\$\\.(input|env|execution|nodes|loop|approval)(?:\\[\\])*"
                        + "(\\.[A-Za-z0-9_-]+(?:\\[\\])*)*")) {
                    throw new IllegalArgumentException("变量路径不在允许作用域或格式无效，位置 " + start);
                }
                return new Token(TokenType.PATH, path, start);
            }
            if (ch == '\'' || ch == '"') {
                return string(ch, start);
            }
            if (Character.isDigit(ch) || ch == '-' && hasNextDigit()) {
                return number(start);
            }
            if (Character.isLetter(ch)) {
                return word(start);
            }
            if (match("&&")) return new Token(TokenType.AND, "&&", start);
            if (match("||")) return new Token(TokenType.OR, "||", start);
            if (match("==")) return new Token(TokenType.EQ, "==", start);
            if (match("!=")) return new Token(TokenType.NE, "!=", start);
            if (match(">=")) return new Token(TokenType.GTE, ">=", start);
            if (match("<=")) return new Token(TokenType.LTE, "<=", start);
            index++;
            return switch (ch) {
                case '!' -> new Token(TokenType.NOT, "!", start);
                case '>' -> new Token(TokenType.GT, ">", start);
                case '<' -> new Token(TokenType.LT, "<", start);
                case '(' -> new Token(TokenType.LPAREN, "(", start);
                case ')' -> new Token(TokenType.RPAREN, ")", start);
                default -> throw new IllegalArgumentException("表达式包含不允许的字符，位置 " + start);
            };
        }

        private Token string(char quote, int start) {
            index++;
            StringBuilder result = new StringBuilder();
            while (index < value.length()) {
                char ch = value.charAt(index++);
                if (ch == quote) {
                    return new Token(TokenType.STRING, result.toString(), start);
                }
                if (ch == '\\') {
                    if (index >= value.length()) {
                        break;
                    }
                    char escaped = value.charAt(index++);
                    result.append(switch (escaped) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case '\\' -> '\\';
                        case '\'', '"' -> escaped;
                        default -> throw new IllegalArgumentException(
                                "字符串包含不支持的转义字符，位置 " + (index - 1));
                    });
                } else {
                    result.append(ch);
                }
            }
            throw new IllegalArgumentException("字符串未闭合，位置 " + start);
        }

        private Token number(int start) {
            if (value.charAt(index) == '-') {
                index++;
            }
            while (index < value.length() && Character.isDigit(value.charAt(index))) {
                index++;
            }
            if (index < value.length() && value.charAt(index) == '.') {
                index++;
                while (index < value.length() && Character.isDigit(value.charAt(index))) {
                    index++;
                }
            }
            return new Token(TokenType.NUMBER, value.substring(start, index), start);
        }

        private Token word(int start) {
            while (index < value.length() && Character.isLetter(value.charAt(index))) {
                index++;
            }
            String word = value.substring(start, index).toLowerCase(java.util.Locale.ROOT);
            return switch (word) {
                case "and" -> new Token(TokenType.AND, word, start);
                case "or" -> new Token(TokenType.OR, word, start);
                case "not" -> new Token(TokenType.NOT, word, start);
                case "true" -> new Token(TokenType.TRUE, word, start);
                case "false" -> new Token(TokenType.FALSE, word, start);
                case "null" -> new Token(TokenType.NULL, word, start);
                default -> throw new IllegalArgumentException("表达式关键字无效，位置 " + start);
            };
        }

        private boolean match(String expected) {
            if (!value.startsWith(expected, index)) {
                return false;
            }
            index += expected.length();
            return true;
        }

        private boolean hasNextDigit() {
            return index + 1 < value.length() && Character.isDigit(value.charAt(index + 1));
        }

        private boolean isPathCharacter(char ch) {
            return Character.isLetterOrDigit(ch) || ch == '.' || ch == '_'
                    || ch == '-' || ch == '[' || ch == ']';
        }

        private void skipWhitespace() {
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
        }
    }

    private record Token(TokenType type, String text, int position) {
    }

    private enum TokenType {
        PATH,
        STRING,
        NUMBER,
        TRUE,
        FALSE,
        NULL,
        AND,
        OR,
        NOT,
        EQ,
        NE,
        GT,
        GTE,
        LT,
        LTE,
        LPAREN,
        RPAREN,
        EOF
    }
}
