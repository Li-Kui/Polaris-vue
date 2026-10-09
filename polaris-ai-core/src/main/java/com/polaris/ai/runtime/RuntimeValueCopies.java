package com.polaris.ai.runtime;

import java.util.*;

/** 运行时 DTO 的递归防御性复制工具。 */
final class RuntimeValueCopies {

    private RuntimeValueCopies() {
    }

    static Map<String, Object> map(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copied = new LinkedHashMap<>();
        source.forEach((key, value) -> copied.put(key, value(value)));
        return Collections.unmodifiableMap(copied);
    }

    static Map<String, Map<String, Object>> nestedMap(
            Map<String, Map<String, Object>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> copied = new LinkedHashMap<>();
        source.forEach((key, value) -> copied.put(key, map(value)));
        return Collections.unmodifiableMap(copied);
    }

    private static Object value(Object source) {
        if (source instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>();
            map.forEach((key, value) -> copied.put(
                    String.valueOf(key), value(value)));
            return Collections.unmodifiableMap(copied);
        }
        if (source instanceof List<?> list) {
            List<Object> copied = new ArrayList<>(list.size());
            list.forEach(item -> copied.add(value(item)));
            return Collections.unmodifiableList(copied);
        }
        if (source instanceof Set<?> set) {
            Set<Object> copied = new LinkedHashSet<>();
            set.forEach(item -> copied.add(value(item)));
            return Collections.unmodifiableSet(copied);
        }
        if (source instanceof byte[] bytes) {
            return bytes.clone();
        }
        if (source instanceof char[] characters) {
            return characters.clone();
        }
        return source;
    }
}
