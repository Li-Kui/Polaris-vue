package com.polaris.platform.connector;

import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 将命名参数转换为 JDBC 占位符，同时避开字符串和标识符内容。 */
@Component
public class NamedSqlParameters {

    public ParsedSql parse(String sql) {
        StringBuilder jdbcSql = new StringBuilder(sql.length());
        List<String> names = new ArrayList<>();
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (singleQuote) {
                jdbcSql.append(current);
                if (current == '\'' && index + 1 < sql.length() && sql.charAt(index + 1) == '\'') {
                    jdbcSql.append(sql.charAt(++index));
                } else if (current == '\'') {
                    singleQuote = false;
                }
                continue;
            }
            if (doubleQuote) {
                jdbcSql.append(current);
                if (current == '"') doubleQuote = false;
                continue;
            }
            if (backtick) {
                jdbcSql.append(current);
                if (current == '`') backtick = false;
                continue;
            }
            if (current == '\'') {
                singleQuote = true;
                jdbcSql.append(current);
                continue;
            }
            if (current == '"') {
                doubleQuote = true;
                jdbcSql.append(current);
                continue;
            }
            if (current == '`') {
                backtick = true;
                jdbcSql.append(current);
                continue;
            }
            if (current == ':' && (index == 0 || sql.charAt(index - 1) != ':')
                    && index + 1 < sql.length() && isIdentifierStart(sql.charAt(index + 1))) {
                int end = index + 2;
                while (end < sql.length() && isIdentifierPart(sql.charAt(end))) end++;
                names.add(sql.substring(index + 1, end));
                jdbcSql.append('?');
                index = end - 1;
                continue;
            }
            jdbcSql.append(current);
        }
        if (singleQuote || doubleQuote || backtick) {
            throw new ServiceException("SQL 字符串或标识符未闭合");
        }
        return new ParsedSql(jdbcSql.toString(), List.copyOf(names));
    }

    private boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_';
    }

    private boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    public record ParsedSql(String sql, List<String> parameterNames) {
    }
}
