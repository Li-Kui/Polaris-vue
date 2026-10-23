package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/** JSON 数字按数值比较，不受 IntNode／LongNode／BigIntegerNode 的解析差异影响；不转换字符串。 */
final class SchemaEnumValues {
    static boolean equal(JsonNode expected, JsonNode actual) {
        if (expected != null && actual != null && expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0;
        }
        return Objects.equals(expected, actual);
    }
    private SchemaEnumValues() { }
}
