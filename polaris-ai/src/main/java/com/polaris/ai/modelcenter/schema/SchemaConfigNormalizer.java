package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 不做类型转换，仅产生字段顺序稳定的深拷贝。 */
@Component
public class SchemaConfigNormalizer {

    public ObjectNode normalize(ObjectNode input) {
        return (ObjectNode) normalizeNode(input);
    }

    private JsonNode normalizeNode(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            fields.addAll(value.properties());
            fields.sort(Comparator.comparing(Map.Entry::getKey));
            fields.forEach(field -> result.set(
                    field.getKey(), normalizeNode(field.getValue())));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> result.add(normalizeNode(item)));
            return result;
        }
        return value.deepCopy();
    }
}
