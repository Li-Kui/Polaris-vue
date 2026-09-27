package com.polaris.ai.modelcenter.schema;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** 仅把 '*' 解释为通配符，不执行 Regex、SpEL 或脚本。 */
final class SafeModelNameMatcher {

    private static final Pattern SAFE_PATTERN =
            Pattern.compile("^[A-Za-z0-9._:/*-]+$");
    private static final int MAX_PATTERN_LENGTH = 160;

    void validate(String pattern) {
        if (pattern == null || pattern.isBlank()
                || pattern.length() > MAX_PATTERN_LENGTH
                || !SAFE_PATTERN.matcher(pattern).matches()) {
            throw new IllegalStateException("Model Profile 仅支持安全字符和 '*' glob");
        }
    }

    boolean matches(String pattern, String value) {
        validate(pattern);
        if (value == null || value.isBlank()) {
            return false;
        }
        int patternIndex = 0;
        int valueIndex = 0;
        int starIndex = -1;
        int starValueIndex = -1;
        while (valueIndex < value.length()) {
            if (patternIndex < pattern.length()
                    && pattern.charAt(patternIndex) == value.charAt(valueIndex)) {
                patternIndex++;
                valueIndex++;
            } else if (patternIndex < pattern.length()
                    && pattern.charAt(patternIndex) == '*') {
                starIndex = patternIndex++;
                starValueIndex = valueIndex;
            } else if (starIndex >= 0) {
                patternIndex = starIndex + 1;
                valueIndex = ++starValueIndex;
            } else {
                return false;
            }
        }
        while (patternIndex < pattern.length()
                && pattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == pattern.length();
    }

    int priority(String pattern) {
        if ("*".equals(pattern)) {
            return 0;
        }
        if (pattern.indexOf('*') < 0) {
            return Integer.MAX_VALUE;
        }
        return (int) pattern.chars().filter(character -> character != '*').count();
    }

    boolean overlaps(String left, String right) {
        validate(left);
        validate(right);
        ArrayDeque<State> pending = new ArrayDeque<>();
        Set<State> visited = new HashSet<>();
        pending.add(new State(0, 0));
        while (!pending.isEmpty()) {
            State state = pending.removeFirst();
            if (!visited.add(state)) {
                continue;
            }
            int leftIndex = state.left();
            int rightIndex = state.right();
            if (leftIndex == left.length() && rightIndex == right.length()) {
                return true;
            }
            if (leftIndex < left.length() && left.charAt(leftIndex) == '*') {
                pending.add(new State(leftIndex + 1, rightIndex));
            }
            if (rightIndex < right.length() && right.charAt(rightIndex) == '*') {
                pending.add(new State(leftIndex, rightIndex + 1));
            }
            if (leftIndex >= left.length() || rightIndex >= right.length()) {
                continue;
            }
            char leftToken = left.charAt(leftIndex);
            char rightToken = right.charAt(rightIndex);
            if (leftToken == '*' && rightToken == '*') {
                continue;
            }
            if (leftToken == '*') {
                pending.add(new State(leftIndex, rightIndex + 1));
            } else if (rightToken == '*') {
                pending.add(new State(leftIndex + 1, rightIndex));
            } else if (leftToken == rightToken) {
                pending.add(new State(leftIndex + 1, rightIndex + 1));
            }
        }
        return false;
    }

    private record State(int left, int right) {}
}
