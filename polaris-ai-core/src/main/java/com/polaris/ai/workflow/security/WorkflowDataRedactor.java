package com.polaris.ai.workflow.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 工作流字段级敏感数据脱敏器。 */
@Component
public class WorkflowDataRedactor {

    private static final String MASK = "***";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "accesstoken",
            "refreshtoken", "idtoken", "apikey", "authorization",
            "proxyauthorization", "cookie", "setcookie", "credential", "credentials",
            "privatekey", "accesskey", "secretkey", "clientsecret");
    private static final Set<String> SENSITIVE_SUFFIXES = Set.of(
            "password", "passwd", "secret", "token", "apikey", "privatekey",
            "accesskey", "secretkey", "credential", "credentials");

    private final ObjectMapper objectMapper;

    public WorkflowDataRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode redact(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return value;
        }
        if (value.isObject()) {
            ObjectNode result = ((ObjectNode) value).deepCopy();
            for (Map.Entry<String, JsonNode> field : List.copyOf(result.properties())) {
                result.set(field.getKey(), isSensitiveKey(field.getKey())
                        ? TextNode.valueOf(MASK) : redact(field.getValue()));
            }
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = ((ArrayNode) value).deepCopy();
            for (int index = 0; index < result.size(); index++) {
                result.set(index, redact(result.get(index)));
            }
            return result;
        }
        if (value.isTextual()) {
            return TextNode.valueOf(redactQuerySecrets(value.textValue()));
        }
        return value.deepCopy();
    }

    public String redactJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            return objectMapper.writeValueAsString(redact(objectMapper.readTree(json)));
        } catch (Exception ignored) {
            return "\"***\"";
        }
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        if (SENSITIVE_KEYS.contains(normalized)) {
            return true;
        }
        return SENSITIVE_SUFFIXES.stream().anyMatch(suffix ->
                normalized.length() > suffix.length() && normalized.endsWith(suffix));
    }

    private String redactQuerySecrets(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.replaceAll(
                "(?i)([?&](?:token|access_token|refresh_token|api_key|apikey|password|secret)=)[^&#\\s]+",
                "$1" + MASK);
    }
}
