package com.polaris.ai.workflow.compiler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.workflow.contract.WorkflowSchemaVersions;
import com.polaris.ai.workflow.definition.WorkflowCompilationResult;
import com.polaris.ai.workflow.definition.WorkflowDefinitionSpec;
import com.polaris.ai.workflow.definition.WorkflowDiagnostic;
import com.polaris.ai.workflow.definition.WorkflowExecutionPlan;
import com.polaris.ai.workflow.spi.WorkflowNodeDescriptor;
import com.polaris.ai.workflow.spi.WorkflowNodeDescriptorResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** 解析、校验并确定性编译工作流定义 JSON。 */
@Component
public class WorkflowDefinitionCompiler {

    private final ObjectMapper objectMapper;
    private final ObjectMapper contractMapper;
    private final WorkflowNodeDescriptorResolver descriptors;
    private final WorkflowExpressionParser expressionParser;
    private final WorkflowDefinitionValidator validator;

    public WorkflowDefinitionCompiler(
            ObjectMapper objectMapper,
            WorkflowNodeDescriptorResolver descriptors) {
        this.objectMapper = objectMapper;
        this.contractMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.descriptors = descriptors;
        this.expressionParser = new WorkflowExpressionParser();
        this.validator = new WorkflowDefinitionValidator(descriptors, expressionParser);
    }

    public WorkflowCompilationResult compile(String definitionJson, String workflowVersionId) {
        if (definitionJson == null || definitionJson.isBlank()) {
            return invalid("DEFINITION_REQUIRED", "$", "工作流定义不能为空");
        }
        WorkflowDefinitionSpec definition;
        try {
            definition = contractMapper.readValue(definitionJson, WorkflowDefinitionSpec.class);
        } catch (JsonProcessingException e) {
            return invalid("DEFINITION_JSON_INVALID", "$", "工作流定义JSON格式无效: "
                    + rootMessage(e));
        }
        List<WorkflowDiagnostic> diagnostics = new ArrayList<>(validator.validate(definition));
        if (diagnostics.stream()
                .anyMatch(item -> item.severity() == WorkflowDiagnostic.Severity.ERROR)) {
            return new WorkflowCompilationResult(null, diagnostics);
        }
        try {
            WorkflowExecutionPlan plan = buildPlan(definition, workflowVersionId);
            plan.setContentHash(calculateContentHash(plan));
            return new WorkflowCompilationResult(plan, diagnostics);
        } catch (RuntimeException e) {
            diagnostics.add(WorkflowDiagnostic.error(
                    "PLAN_COMPILATION_FAILED", null, "$", "执行计划编译失败: " + e.getMessage()));
            return new WorkflowCompilationResult(null, diagnostics);
        }
    }

    private WorkflowExecutionPlan buildPlan(
            WorkflowDefinitionSpec definition, String workflowVersionId) {
        WorkflowExecutionPlan plan = new WorkflowExecutionPlan();
        plan.setPlanSchemaVersion(WorkflowSchemaVersions.EXECUTION_PLAN);
        plan.setDefinitionSchemaVersion(WorkflowSchemaVersions.DEFINITION);
        plan.setWorkflowVersionId(
                workflowVersionId == null || workflowVersionId.isBlank()
                        ? "draft:" + definition.getMetadata().getCode() : workflowVersionId);
        plan.setInputs(definition.getInputs());

        definition.getEdges().stream()
                .filter(edge -> "__start__".equals(edge.getSource()))
                .map(WorkflowDefinitionSpec.Edge::getTarget)
                .sorted()
                .forEach(plan.getEntryNodeIds()::add);

        definition.getNodes().stream()
                .sorted(Comparator.comparing(WorkflowDefinitionSpec.Node::getId))
                .map(this::compileNode)
                .forEach(plan.getNodes()::add);

        definition.getEdges().stream()
                .sorted(Comparator.comparing(WorkflowDefinitionSpec.Edge::getSource)
                        .thenComparing(this::priority)
                        .thenComparing(WorkflowDefinitionSpec.Edge::getTarget)
                        .thenComparing(edge -> String.valueOf(edge.getSourcePort())))
                .map(this::compileEdge)
                .forEach(plan.getEdges()::add);

        plan.setOutputs(compileBindings(definition.getOutputs()));
        WorkflowDefinitionSpec.Policies policies = definition.getPolicies();
        Map<String, Object> compiledPolicies = new LinkedHashMap<>();
        compiledPolicies.put("timeoutSeconds", policies.getTimeoutSeconds());
        compiledPolicies.put("maxNodeRuns", policies.getMaxNodeRuns());
        compiledPolicies.put("maxParallelism", policies.getMaxParallelism());
        if (policies.getTokenBudget() != null) {
            compiledPolicies.put("tokenBudget", policies.getTokenBudget());
        }
        if (policies.getCostBudget() != null) {
            compiledPolicies.put("costBudget", policies.getCostBudget());
        }
        plan.setPolicies(compiledPolicies);
        return plan;
    }

    private WorkflowExecutionPlan.PlanNode compileNode(WorkflowDefinitionSpec.Node node) {
        WorkflowNodeDescriptor descriptor = descriptors.find(node.getType(), node.getTypeVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "节点处理器不存在: " + node.getType() + ":" + node.getTypeVersion()));
        WorkflowExecutionPlan.PlanNode result = new WorkflowExecutionPlan.PlanNode();
        result.setId(node.getId());
        result.setType(node.getType());
        result.setHandlerVersion(descriptor.handlerVersion());
        result.setInputMapping(compileBindings(node.getInputMapping()));
        result.setConfig(node.getConfig());
        result.setSideEffect(descriptor.sideEffect().name());
        result.setOnError(node.getOnError() == null || node.getOnError().isBlank()
                ? "FAIL" : node.getOnError());
        result.setCompensationNodeId(node.getCompensationNodeId());
        result.setTimeoutSeconds(node.getTimeoutSeconds() == null ? 120 : node.getTimeoutSeconds());
        result.setRetryPolicy(node.getRetryPolicy());
        result.setResourceRefs(node.getResourceRefs() == null ? List.of() : node.getResourceRefs());
        return result;
    }

    private WorkflowExecutionPlan.PlanEdge compileEdge(WorkflowDefinitionSpec.Edge edge) {
        WorkflowExecutionPlan.PlanEdge result = new WorkflowExecutionPlan.PlanEdge();
        result.setSource(edge.getSource());
        result.setSourcePort(edge.getSourcePort());
        result.setTarget(edge.getTarget());
        result.setKind(edge.getKind());
        result.setPriority(priority(edge));
        result.setDefaultEdge(Boolean.TRUE.equals(edge.getDefaultEdge()));
        if (edge.getCondition() != null && !Boolean.TRUE.equals(edge.getDefaultEdge())) {
            result.setExpressionAst(expressionParser.parse(edge.getCondition().getExpression()));
            result.setConditionOnError(edge.getCondition().getOnError());
        }
        return result;
    }

    private Map<String, WorkflowExecutionPlan.PlanValueBinding> compileBindings(
            Map<String, WorkflowDefinitionSpec.ValueBinding> bindings) {
        Map<String, WorkflowExecutionPlan.PlanValueBinding> result = new LinkedHashMap<>();
        if (bindings == null) {
            return result;
        }
        new TreeMap<>(bindings).forEach((key, binding) -> {
            WorkflowExecutionPlan.PlanValueBinding compiled =
                    new WorkflowExecutionPlan.PlanValueBinding();
            if (binding.getExpression() != null && !binding.getExpression().isBlank()) {
                compiled.setExpressionAst(expressionParser.parse(binding.getExpression()));
            } else {
                compiled.setValue(binding.getValue());
            }
            result.put(key, compiled);
        });
        return result;
    }

    private int priority(WorkflowDefinitionSpec.Edge edge) {
        if (edge.getCondition() != null && edge.getCondition().getPriority() != null) {
            return edge.getCondition().getPriority();
        }
        return Boolean.TRUE.equals(edge.getDefaultEdge()) ? 10001 : 0;
    }

    private String calculateContentHash(WorkflowExecutionPlan plan) {
        try {
            ObjectNode content = objectMapper.valueToTree(plan);
            content.remove("contentHash");
            content.remove("workflowVersionId");
            JsonNode canonical = canonicalize(content);
            byte[] bytes = objectMapper.writeValueAsString(canonical)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算执行计划内容哈希", e);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        ObjectNode result = objectMapper.createObjectNode();
        Map<String, JsonNode> fields = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> field = iterator.next();
            fields.put(field.getKey(), field.getValue());
        }
        fields.forEach((key, value) -> result.set(key, canonicalize(value)));
        return result;
    }

    private WorkflowCompilationResult invalid(String code, String fieldPath, String message) {
        return new WorkflowCompilationResult(null,
                List.of(WorkflowDiagnostic.error(code, null, fieldPath, message)));
    }

    private String rootMessage(JsonProcessingException error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? error.getOriginalMessage() : cause.getMessage();
    }
}
