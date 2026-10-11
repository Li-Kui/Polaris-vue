package com.polaris.platform.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.Type;

/** SDK对缺失content等字段有兼容默认值；调用结果必须先检查原结构，不能默认为成功空结果。 */
final class McpStrictJsonMapper implements McpJsonMapper {
    private final JsonMapper json;
    private final JacksonMcpJsonMapper delegate;

    McpStrictJsonMapper(JsonMapper json) { this.json = json; delegate = new JacksonMcpJsonMapper(json); }

    private void validate(Object value, Type type) {
        if (!McpSchema.CallToolResult.class.equals(type)) return;
        JsonNode result = json.valueToTree(value);
        if (!result.isObject() || !result.path("content").isArray()
                || result.has("isError") && !result.get("isError").isBoolean()
                || result.has("structuredContent") && !result.get("structuredContent").isObject()) throw invalid();
        for (JsonNode content : result.get("content")) {
            if (!content.isObject() || !string(content, "type")) throw invalid();
            boolean valid = switch (content.path("type").asText()) {
                case "text" -> string(content, "text");
                case "image", "audio" -> string(content, "data") && string(content, "mimeType");
                case "resource_link" -> string(content, "uri") && string(content, "name");
                case "resource" -> resource(content.path("resource"));
                default -> false;
            };
            if (!valid) throw invalid();
        }
    }

    private boolean resource(JsonNode resource) {
        return resource.isObject() && string(resource, "uri")
                && (string(resource, "text") ^ string(resource, "blob"));
    }

    private boolean string(JsonNode value, String name) { return value.path(name).isString(); }
    private McpInvocationException invalid() { return new McpInvocationException(McpInvocationException.Code.PROTOCOL_ERROR); }

    @Override public <T> T readValue(String value, Class<T> type) throws IOException {
        validate(json.readTree(value), type); return delegate.readValue(value, type);
    }
    @Override public <T> T readValue(byte[] value, Class<T> type) throws IOException {
        validate(json.readTree(value), type); return delegate.readValue(value, type);
    }
    @Override public <T> T readValue(String value, TypeRef<T> type) throws IOException {
        validate(json.readTree(value), type.getType()); return delegate.readValue(value, type);
    }
    @Override public <T> T readValue(byte[] value, TypeRef<T> type) throws IOException {
        validate(json.readTree(value), type.getType()); return delegate.readValue(value, type);
    }
    @Override public <T> T convertValue(Object value, Class<T> type) {
        validate(value, type); return delegate.convertValue(value, type);
    }
    @Override public <T> T convertValue(Object value, TypeRef<T> type) {
        validate(value, type.getType()); return delegate.convertValue(value, type);
    }
    @Override public String writeValueAsString(Object value) throws IOException { return delegate.writeValueAsString(value); }
    @Override public byte[] writeValueAsBytes(Object value) throws IOException { return delegate.writeValueAsBytes(value); }
}
