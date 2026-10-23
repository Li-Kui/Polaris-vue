package com.polaris.ai.agent.collaboration;

import com.polaris.ai.agent.collaboration.AgentCollaborationPorts.ToolArgumentValidator;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 完整计划一次验证；传输和模型都不能更改服务器限定的参数来源。 */
public final class AgentPlanValidator {
    static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Set<String> ROOT_KEYS = Set.of("type", "properties", "required",
            "additionalProperties", "description", "title", "$schema");
    private final ToolArgumentValidator values;
    public AgentPlanValidator(ToolArgumentValidator values) { this.values = Objects.requireNonNull(values); }

    public void validateBindingDefinition(ToolBinding binding) {
        ObjectNode schema = parseObject(binding.inputSchemaJson(), 32768, Code.UNSUPPORTED_SCHEMA);
        values.validateDefinition(binding.inputSchemaJson());
        if (!"object".equals(schema.path("type").asString()) || !schema.path("properties").isObject()
                || schema.has("additionalProperties") && !schema.get("additionalProperties").isBoolean()
                || schema.propertyNames().stream().anyMatch(key -> !ROOT_KEYS.contains(key))) {
            throw new Failure(Code.UNSUPPORTED_SCHEMA);
        }
        rejectReferences(schema);
        ObjectNode fixed = parseObject(binding.fixedArgumentsJson(), 8192, Code.ARGUMENTS_REJECTED);
        if (fixed.propertyNames().stream().anyMatch(key -> !schema.path("properties").has(key))
                || binding.queryArgument() != null && (fixed.has(binding.queryArgument())
                    || !schema.path("properties").has(binding.queryArgument()))) {
            throw new Failure(Code.ARGUMENTS_REJECTED);
        }
    }

    public List<ToolDefinition> prepare(Request request) {
        return prepareDefinitions(request.externalQuery(), request.tools());
    }

    /** 本地预检可直接验证明确的工具集合，不需要伪造任何工作流执行身份。 */
    public List<ToolDefinition> prepareDefinitions(String externalQuery, List<ToolBinding> tools) {
        if (externalQuery == null || tools == null || tools.size() > 6) throw new Failure(Code.INVALID_REQUEST);
        List<ToolDefinition> definitions = new ArrayList<>();
        int bytes = 0;
        for (ToolBinding binding : tools) {
            ObjectNode schema = parseObject(binding.inputSchemaJson(), 32768, Code.UNSUPPORTED_SCHEMA);
            values.validateDefinition(binding.inputSchemaJson());
            if (!"object".equals(schema.path("type").asString()) || !schema.path("properties").isObject()
                    || schema.has("additionalProperties") && !schema.get("additionalProperties").isBoolean()
                    || schema.propertyNames().stream().anyMatch(key -> !ROOT_KEYS.contains(key))) {
                throw new Failure(Code.UNSUPPORTED_SCHEMA);
            }
            // SDK 根对象只支持有限关键字；局部引用依赖根定义，首版不静默丢失它们。
            rejectReferences(schema);
            ObjectNode expected = expectedArguments(externalQuery, binding);
            values.validateArguments(binding.inputSchemaJson(), JSON.writeValueAsString(expected));
            ObjectNode projected = JSON.createObjectNode();
            projected.put("type", "object");
            projected.put("additionalProperties", false);
            ObjectNode properties = projected.putObject("properties");
            for (var field : expected.properties()) {
                JsonNode original = schema.path("properties").get(field.getKey());
                if (original == null) throw new Failure(Code.ARGUMENTS_REJECTED);
                ObjectNode constraint = JSON.createObjectNode();
                constraint.putArray("allOf").add(original.deepCopy()).add(JSON.createObjectNode().set("const", field.getValue()));
                properties.set(field.getKey(), constraint);
                projected.withArray("required").add(field.getKey());
            }
            String restricted = JSON.writeValueAsString(projected);
            int toolBytes = restricted.getBytes(StandardCharsets.UTF_8).length;
            bytes += toolBytes;
            if (toolBytes > 32768 || bytes > 98304) throw new Failure(Code.UNSUPPORTED_SCHEMA);
            definitions.add(new ToolDefinition(binding.alias(), binding.purpose(), restricted));
        }
        return List.copyOf(definitions);
    }

    public List<ToolIntent> validate(Request request, Plan plan) {
        if (plan == null || plan.calls().size() > request.maxCalls()) throw new Failure(Code.INVALID_PLAN);
        Map<String, ToolBinding> tools = new HashMap<>();
        request.tools().forEach(tool -> tools.put(tool.alias(), tool));
        Set<String> callIds = new HashSet<>(), duplicates = new HashSet<>();
        List<ToolIntent> result = new ArrayList<>();
        for (Proposal proposal : plan.calls()) {
            if (proposal.callId() == null || !proposal.callId().matches("[A-Za-z0-9_.:-]{1,64}")
                    || !callIds.add(proposal.callId()) || !tools.containsKey(proposal.alias())) {
                throw new Failure(Code.INVALID_PLAN);
            }
            ToolBinding binding = tools.get(proposal.alias());
            ObjectNode arguments = parseObject(proposal.argumentsJson(), 8192, Code.ARGUMENTS_REJECTED);
            if (!arguments.equals(expectedArguments(request.externalQuery(), binding))) throw new Failure(Code.ARGUMENTS_REJECTED);
            values.validateArguments(binding.inputSchemaJson(), proposal.argumentsJson());
            String canonical = JSON.writeValueAsString(sorted(arguments));
            if (!duplicates.add(binding.snapshot().toolId() + ":" + canonical)) throw new Failure(Code.INVALID_PLAN);
            result.add(new ToolIntent(request.context(), result.size() + 1, binding.snapshot(), canonical));
        }
        return List.copyOf(result);
    }

    static ObjectNode parseObject(String value, int byteLimit, Code failure) {
        try {
            if (value == null || value.getBytes(StandardCharsets.UTF_8).length > byteLimit) throw new Failure(failure);
            JsonNode node = JSON.readTree(value);
            if (node == null || !node.isObject()) throw new Failure(failure);
            checkDepth(node, 0, failure);
            return (ObjectNode) node;
        } catch (Failure error) { throw error; }
        catch (RuntimeException error) { throw new Failure(failure); }
    }

    private ObjectNode expectedArguments(String query, ToolBinding binding) {
        ObjectNode result = parseObject(binding.fixedArgumentsJson(), 8192, Code.ARGUMENTS_REJECTED);
        if (binding.queryArgument() != null) {
            if (result.has(binding.queryArgument())) throw new Failure(Code.ARGUMENTS_REJECTED);
            result.put(binding.queryArgument(), query);
        }
        if (JSON.writeValueAsBytes(result).length > 8192) throw new Failure(Code.ARGUMENTS_REJECTED);
        return result;
    }

    private static void checkDepth(JsonNode node, int depth, Code code) {
        if (depth > 16) throw new Failure(code);
        for (JsonNode child : node) checkDepth(child, depth + 1, code);
    }
    private void rejectReferences(JsonNode node) {
        if (node.isObject() && (node.has("$ref") || node.has("$defs") || node.has("definitions"))) {
            throw new Failure(Code.UNSUPPORTED_SCHEMA);
        }
        for (JsonNode child : node) rejectReferences(child);
    }
    static JsonNode sorted(JsonNode value) {
        if (value.isObject()) {
            ObjectNode object = JSON.createObjectNode();
            value.propertyNames().stream().sorted().forEach(key -> object.set(key, sorted(value.get(key))));
            return object;
        }
        if (value.isArray()) {
            var array = JSON.createArrayNode();
            value.forEach(child -> array.add(sorted(child)));
            return array;
        }
        return value;
    }
    public static String requestHash(Request request) {
        try {
            ObjectNode value = JSON.createObjectNode();
            value.set("context", JSON.valueToTree(request.context()));
            value.set("model", JSON.valueToTree(request.model()));
            var bindings = value.putArray("tools");
            for (ToolBinding tool : request.tools()) {
                ObjectNode binding = JSON.valueToTree(tool);
                binding.set("inputSchemaJson", parseObject(tool.inputSchemaJson(), 32768, Code.UNSUPPORTED_SCHEMA));
                binding.set("fixedArgumentsJson", parseObject(tool.fixedArgumentsJson(), 8192, Code.ARGUMENTS_REJECTED));
                bindings.add(binding);
            }
            value.put("rolePrompt", request.rolePrompt()).put("question", request.question()).put("externalQuery", request.externalQuery())
                    .put("knowledgeRequired", request.knowledgeRequired()).put("maxCalls", request.maxCalls())
                    .put("budgetMillis", request.budget().toMillis());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(sorted(value))));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256不可用"); }
    }
}
