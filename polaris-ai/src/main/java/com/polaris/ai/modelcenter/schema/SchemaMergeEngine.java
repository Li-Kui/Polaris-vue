package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 按 object 递归、scalar 覆盖、array 替换语义合并 Schema/Profile。 */
@Component
public class SchemaMergeEngine {

    public JsonNode merge(JsonNode base, JsonNode... overlays) {
        JsonNode result = base == null || base.isNull()
                ? JsonNodeFactory.instance.objectNode() : base.deepCopy();
        if (overlays == null) {
            return result;
        }
        for (JsonNode overlay : overlays) {
            if (overlay == null || overlay.isNull()) {
                continue;
            }
            if (isRemoveMarker(overlay)) {
                throw new IllegalStateException("$remove 不能用于 Schema 根节点");
            }
            result = mergeNode(result, overlay, "$");
        }
        return result;
    }

    private JsonNode mergeNode(
            JsonNode base,
            JsonNode overlay,
            String path) {
        if (overlay.isArray()) {
            ensureNoRemoveInArray(overlay, path);
            return overlay.deepCopy();
        }
        if (!overlay.isObject()) {
            return overlay.deepCopy();
        }
        if (isRemoveMarker(overlay)) {
            throw new IllegalStateException("$remove 位置非法: " + path);
        }

        ObjectNode result = base != null && base.isObject()
                ? ((ObjectNode) base).deepCopy()
                : JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, JsonNode> field : overlay.properties()) {
            String name = field.getKey();
            JsonNode value = field.getValue();
            String childPath = path + "." + name;
            if ("$remove".equals(name)) {
                throw new IllegalStateException("$remove 指令结构非法: " + path);
            }
            if (isRemoveMarker(value)) {
                if (!result.has(name)) {
                    throw new IllegalStateException(
                            "$remove 引用不存在节点: " + childPath);
                }
                result.remove(name);
                continue;
            }
            JsonNode current = result.get(name);
            result.set(name, mergeNode(current, value, childPath));
        }
        return result;
    }

    private boolean isRemoveMarker(JsonNode node) {
        return node != null && node.isObject() && node.size() == 1
                && node.path("$remove").isBoolean()
                && node.path("$remove").booleanValue();
    }

    private void ensureNoRemoveInArray(JsonNode array, String path) {
        for (int index = 0; index < array.size(); index++) {
            JsonNode value = array.get(index);
            if (containsRemoveDirective(value)) {
                throw new IllegalStateException(
                        "$remove 不能出现在 array 中: " + path + "[" + index + "]");
            }
        }
    }

    private boolean containsRemoveDirective(JsonNode node) {
        if (node == null || !node.isContainerNode()) {
            return false;
        }
        if (node.isObject() && node.has("$remove")) {
            return true;
        }
        for (JsonNode child : node) {
            if (containsRemoveDirective(child)) {
                return true;
            }
        }
        return false;
    }
}
