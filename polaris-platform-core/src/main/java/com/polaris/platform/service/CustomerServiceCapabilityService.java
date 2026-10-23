package com.polaris.platform.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.polaris.ai.customer.application.CustomerServiceCapability;
import com.polaris.ai.workflow.application.WorkflowDefinitionApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowDefinitionView;
import com.polaris.ai.workflow.spi.WorkflowNodeDescriptorResolver;
import com.polaris.ai.workflow.spi.WorkflowSideEffect;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** 业务能力配置与发布预检；本阶段不提供运行入口，不触发工作流或外部业务。 */
@Service
public class CustomerServiceCapabilityService {
    private static final Set<String> READ_ONLY_NODES = Set.of("transform", "condition", "parallel", "join", "database_query");
    private static final Set<String> SCALAR_TYPES = Set.of("string", "integer", "number", "boolean");
    private final CustomerServiceAccess access;
    private final WorkflowDefinitionApplicationFacade workflows;
    private final WorkflowNodeDescriptorResolver descriptors;
    private final ObjectMapper json;

    public CustomerServiceCapabilityService(CustomerServiceAccess access, WorkflowDefinitionApplicationFacade workflows,
            WorkflowNodeDescriptorResolver descriptors, ObjectMapper json) {
        this.access = access;
        this.workflows = workflows;
        this.descriptors = descriptors;
        this.json = json;
    }

    public List<WorkflowChoice> workflows() {
        Long tenantId = access.requireAdmin().tenantId();
        return workflows.listDefinitions().stream()
                .filter(value -> tenantId.equals(value.tenantId()) && "TENANT".equals(value.ownerType())
                        && "ACTIVE".equals(value.status()) && value.currentPublishedVersionId() != null)
                .map(value -> new WorkflowChoice(value.id(), value.workflowName())).toList();
    }

    public List<VersionChoice> versions(Long definitionId) {
        requireWorkflow(definitionId);
        return workflows.listVersions(definitionId).stream()
                .filter(value -> Objects.equals(definitionId, value.definitionId()) && "PUBLISHED".equals(value.status()))
                .map(value -> new VersionChoice(value.versionId(), value.versionNo())).toList();
    }

    public Contract contract(Long definitionId, String versionId) {
        requireWorkflow(definitionId);
        var version = workflows.getVersion(definitionId, versionId);
        if (version == null || !Objects.equals(definitionId, version.definitionId())
                || !Objects.equals(versionId, version.versionId()) || !"PUBLISHED".equals(version.status())) {
            throw unavailable();
        }
        try {
            if (version.definitionJson() == null || version.definitionJson().length() > 262144) throw unsupported();
            JsonNode definition = json.readTree(version.definitionJson());
            JsonNode nodes = definition.path("nodes");
            if (!nodes.isArray() || nodes.isEmpty() || nodes.size() > 50) throw unsupported();
            for (JsonNode node : nodes) {
                String type = node.path("type").asString();
                var descriptor = descriptors.find(type, node.path("typeVersion").asString()).orElseThrow(this::unsupported);
                // 子流程即使声明 NONE，也不能据此证明整条调用链只读；HTTP GET 同样不自动信任。
                if (!READ_ONLY_NODES.contains(type) || descriptor.sideEffect() != WorkflowSideEffect.NONE
                        && descriptor.sideEffect() != WorkflowSideEffect.READ
                        || node.hasNonNull("compensationNodeId")) throw unsupported();
            }
            JsonNode schema = definition.path("inputs");
            JsonNode properties = schema.path("properties");
            if (!"object".equals(schema.path("type").asString()) || !properties.isObject()
                    || properties.size() > 10 || !schema.path("additionalProperties").isBoolean()
                    || schema.path("additionalProperties").asBoolean() || schema.has("$ref")
                    || schema.has("oneOf") || schema.has("anyOf") || schema.has("allOf")) throw unsupported();
            Set<String> required = new HashSet<>();
            if (schema.has("required")) {
                if (!schema.get("required").isArray()) throw unsupported();
                for (JsonNode item : schema.get("required")) {
                    if (!item.isString() || !properties.has(item.asString())) throw unsupported();
                    required.add(item.asString());
                }
            }
            List<InputParameter> inputs = new ArrayList<>();
            for (var entry : properties.properties()) {
                String type = entry.getValue().path("type").asString();
                if (!field(entry.getKey()) || !SCALAR_TYPES.contains(type) || entry.getValue().has("$ref")
                        || entry.getValue().has("oneOf") || entry.getValue().has("anyOf")
                        || entry.getValue().has("allOf")) throw unsupported();
                inputs.add(parameter(entry.getKey(), type, required.contains(entry.getKey()), entry.getValue()));
            }
            JsonNode outputs = definition.path("outputs");
            if (!outputs.isObject() || outputs.isEmpty() || outputs.size() > 30) throw unsupported();
            List<String> fields = new ArrayList<>();
            for (var entry : outputs.properties()) {
                if (!field(entry.getKey())) throw unsupported();
                fields.add(entry.getKey());
            }
            return new Contract(definitionId, versionId, version.versionNo(), List.copyOf(inputs), List.copyOf(fields),
                    "CONFIGURATION_ONLY", schema);
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unsupported();
        }
    }

    public Contract check(CustomerServiceCapability capability) {
        var contract = contract(capability.definitionId(), capability.versionId());
        Set<String> parameters = new HashSet<>();
        for (var input : contract.inputs()) {
            parameters.add(input.name());
            String source = capability.inputSources().get(input.name());
            if (source == null || !"USER_INPUT".equals(source) && !"integer".equals(input.type())) {
                throw new ServiceException("CAPABILITY_CONTRACT_CHANGED: 输入映射不完整或身份参数类型不匹配", 409);
            }
        }
        if (!parameters.equals(capability.inputSources().keySet())
                || !contract.outputFields().containsAll(capability.outputFields())) {
            throw new ServiceException("CAPABILITY_CONTRACT_CHANGED: 输入或输出已不属于固定版本契约", 409);
        }
        return contract;
    }

    public void validate(List<CustomerServiceCapability> capabilities) {
        for (var capability : capabilities) check(capability);
    }

    /** 用户明确选择先验无外部业务的测试闭环；数据库能力仍只允许配置。 */
    public Contract testContract(CustomerServiceCapability capability) {
        var contract = check(capability);
        var version = workflows.getVersion(capability.definitionId(), capability.versionId());
        if (version == null || !"PUBLISHED".equals(version.status())
                || !Objects.equals(capability.definitionId(), version.definitionId())
                || !Objects.equals(capability.versionId(), version.versionId())) throw unavailable();
        JsonNode definition = json.readTree(version.definitionJson());
        for (JsonNode node : definition.path("nodes")) {
            var descriptor = descriptors.find(node.path("type").asString(), node.path("typeVersion").asString()).orElseThrow(this::unsupported);
            if ("database_query".equals(node.path("type").asString())
                    || descriptor.sideEffect() != WorkflowSideEffect.NONE
                    || !descriptor.requiredResourceKinds().isEmpty()
                    || node.has("resourceRefs") && !node.get("resourceRefs").isEmpty()) {
                throw new ServiceException("CAPABILITY_TEST_ONLY: 当前只运行无外部资源的确定性工作流，业务能力稍后接入", 409);
            }
        }
        return contract;
    }

    private WorkflowDefinitionView requireWorkflow(Long definitionId) {
        Long tenantId = access.requireAdmin().tenantId();
        if (definitionId == null || definitionId <= 0) throw unavailable();
        var definition = workflows.getDefinition(definitionId);
        if (definition == null || !Objects.equals(definitionId, definition.id())
                || !tenantId.equals(definition.tenantId()) || !"TENANT".equals(definition.ownerType())
                || !"ACTIVE".equals(definition.status())) throw unavailable();
        return definition;
    }

    private boolean field(String value) { return value != null && value.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"); }

    private InputParameter parameter(String name, String type, boolean required, JsonNode schema) {
        String label = metadata(schema, "title", name, 64);
        String description = metadata(schema, "description", "", 300);
        if (schema.has("examples")) {
            if (!schema.get("examples").isArray() || schema.get("examples").size() > 5) throw unsupported();
            for (var value : schema.get("examples")) if (!scalar(value, type) || value.isString() && value.asString().length() > 200) throw unsupported();
        }
        JsonNode example = schema.path("examples").isArray() && !schema.get("examples").isEmpty() ? schema.get("examples").get(0) : null;
        var constraints = json.createObjectNode();
        for (String key : List.of("enum", "const", "minLength", "maxLength", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "pattern")) {
            if (!schema.has(key)) continue;
            var value = schema.get(key);
            if ("enum".equals(key)) {
                if (!value.isArray() || value.isEmpty() || value.size() > 20) throw unsupported();
                for (var item : value) if (!scalar(item, type) || item.isString() && item.asString().length() > 100) throw unsupported();
            } else if ("const".equals(key)) {
                if (!scalar(value, type) || value.isString() && value.asString().length() > 100) throw unsupported();
            } else if ("pattern".equals(key)) {
                if (!"string".equals(type) || !value.isString() || value.asString().length() > 256) throw unsupported();
                try { java.util.regex.Pattern.compile(value.asString()); } catch (java.util.regex.PatternSyntaxException invalid) { throw unsupported(); }
            } else if (key.endsWith("Length")) {
                if (!"string".equals(type) || !value.isIntegralNumber() || value.bigIntegerValue().signum() < 0
                        || value.bigIntegerValue().compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE)) > 0) throw unsupported();
            } else if (!Set.of("integer", "number").contains(type) || !value.isNumber() || value.toString().length() > 64) throw unsupported();
            constraints.set(key, value.deepCopy());
        }
        if (constraints.has("minLength") && constraints.has("maxLength")
                && constraints.path("minLength").asInt() > constraints.path("maxLength").asInt()) throw unsupported();
        for (String minimum : List.of("minimum", "exclusiveMinimum")) for (String maximum : List.of("maximum", "exclusiveMaximum")) {
            if (!constraints.has(minimum) || !constraints.has(maximum)) continue;
            int comparison = constraints.get(minimum).decimalValue().compareTo(constraints.get(maximum).decimalValue());
            if (comparison > 0 || comparison == 0 && (minimum.startsWith("exclusive") || maximum.startsWith("exclusive"))) throw unsupported();
        }
        return new InputParameter(name, type, required, label, description, example, constraints);
    }

    private String metadata(JsonNode schema, String key, String fallback, int limit) {
        if (!schema.has(key)) return fallback;
        var value = schema.get(key);
        if (!value.isString() || value.asString().length() > limit) throw unsupported();
        return value.asString().isBlank() ? fallback : value.asString().strip();
    }

    private boolean scalar(JsonNode value, String type) {
        return switch (type) {
            case "string" -> value.isString();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            default -> false;
        };
    }
    private ServiceException unavailable() { return new ServiceException("CAPABILITY_UNAVAILABLE: 工作流或固定发布版本不可用", 409); }
    private ServiceException unsupported() { return new ServiceException("CAPABILITY_UNSUPPORTED: 当前仅支持确定性转换、条件、并行汇聚和数据库只读节点，以及最多 10 个封闭标量输入；不支持子流程、HTTP、工具或写入节点", 409); }

    public record WorkflowChoice(Long id, String name) { }
    public record VersionChoice(String versionId, Integer versionNo) { }
    public record InputParameter(String name, String type, boolean required, String label, String description, JsonNode example, JsonNode constraints) {
        public InputParameter(String name, String type, boolean required) { this(name, type, required, name, "", null, null); }
        public InputParameter {
            example = example == null ? null : example.deepCopy();
            constraints = constraints == null ? null : constraints.deepCopy();
        }
        @Override public JsonNode example() { return example == null ? null : example.deepCopy(); }
        @Override public JsonNode constraints() { return constraints == null ? null : constraints.deepCopy(); }
    }
    public record Contract(Long definitionId, String versionId, Integer versionNo, List<InputParameter> inputs,
                           List<String> outputFields, String executionStatus, @JsonIgnore JsonNode inputSchema) {
        public Contract(Long definitionId, String versionId, Integer versionNo, List<InputParameter> inputs,
                List<String> outputFields, String executionStatus) {
            this(definitionId, versionId, versionNo, inputs, outputFields, executionStatus, null);
        }
        public Contract { inputSchema = inputSchema == null ? null : inputSchema.deepCopy(); }
        @Override @JsonIgnore public JsonNode inputSchema() { return inputSchema == null ? null : inputSchema.deepCopy(); }
    }
}
