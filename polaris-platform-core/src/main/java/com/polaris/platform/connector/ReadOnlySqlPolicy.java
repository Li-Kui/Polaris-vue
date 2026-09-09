package com.polaris.platform.connector;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/** 工作流和中台只读数据源查询使用的保守 SQL 安全策略。 */
@Component
public class ReadOnlySqlPolicy {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(insert|update|delete|merge|replace|upsert|drop|alter|truncate|create|grant|revoke|call|execute|copy|load|outfile|dumpfile|lock|unlock)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGEROUS_FUNCTION = Pattern.compile(
            "\\b(sleep|benchmark|load_file|pg_sleep|dblink_connect|sys_eval|sys_exec)\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    public String validate(String sql) {
        if (sql == null || sql.isBlank() || sql.length() > 100_000) {
            throw new IllegalArgumentException("只读SQL不能为空且不能超过100KB");
        }
        String value = sql.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (!startsWithQuery(lower)) throw new IllegalArgumentException("只读数据源仅允许SELECT或WITH查询");
        if (value.indexOf(';') >= 0 || lower.contains("--") || lower.contains("/*")
                || lower.contains("*/") || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("只读SQL不允许多语句或注释");
        }
        String tokenView = maskStringLiterals(value);
        if (FORBIDDEN.matcher(tokenView).find()
                || DANGEROUS_FUNCTION.matcher(tokenView).find()
                || Pattern.compile("\\bfor\\s+update\\b", Pattern.CASE_INSENSITIVE)
                .matcher(tokenView).find()) {
            throw new IllegalArgumentException("只读SQL包含不允许的关键字或函数");
        }
        try {
            Statement statement = CCJSqlParserUtil.parse(value);
            if (!(statement instanceof Select)) {
                throw new IllegalArgumentException("只读数据源仅允许查询语句");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("只读SQL语法无效");
        }
        return value;
    }

    private boolean startsWithQuery(String value) {
        return startsWithKeyword(value, "select") || startsWithKeyword(value, "with");
    }

    private boolean startsWithKeyword(String value, String keyword) {
        if (!value.startsWith(keyword)) return false;
        return value.length() == keyword.length()
                || Character.isWhitespace(value.charAt(keyword.length()));
    }

    private String maskStringLiterals(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        boolean single = false;
        boolean doubled = false;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (single) {
                if (current == '\'' && index + 1 < sql.length() && sql.charAt(index + 1) == '\'') {
                    result.append("  ");
                    index++;
                    continue;
                }
                if (current == '\'') single = false;
                result.append(' ');
            } else if (doubled) {
                if (current == '"') doubled = false;
                result.append(' ');
            } else if (current == '\'') {
                single = true;
                result.append(' ');
            } else if (current == '"') {
                doubled = true;
                result.append(' ');
            } else {
                result.append(current);
            }
        }
        if (single || doubled) {
            throw new IllegalArgumentException("只读SQL字符串未闭合");
        }
        return result.toString();
    }
}
