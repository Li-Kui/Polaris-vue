package com.polaris.ai.workflow.compiler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.workflow.application.WorkflowResolvedNodeSchemaView;
import com.polaris.ai.workflow.contract.WorkflowSchemaVersions;
import com.polaris.ai.workflow.definition.WorkflowCompilationResult;
import com.polaris.ai.workflow.definition.WorkflowDefinitionSpec;
import com.polaris.ai.workflow.definition.WorkflowDiagnostic;
import com.polaris.ai.workflow.definition.WorkflowExecutionPlan;
import com.polaris.ai.workflow.runtime.WorkflowOutputSchemaGovernance;
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
    private com.polaris.ai.workflow.service.WorkflowSubWorkflowService subWorkflows;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSubWorkflows(com.polaris.ai.workflow.service.WorkflowSubWorkflowService service) {
        this.subWorkflows = service;
    }

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
        return compile(definitionJson, workflowVersionId, List.of());
    }

    public WorkflowCompilationResult compile(
            String definitionJson,
            String workflowVersionId,
            List<WorkflowResolvedNodeSchemaView> resolvedSchemas) {
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
            WorkflowExecutionPlan plan = buildPlan(
                    definition, workflowVersionId, schemaSnapshots(resolvedSchemas));
            freezeSubWorkflows(definition, plan);
            plan.setContentHash(calculateContentHash(plan));
            return new WorkflowCompilationResult(plan, diagnostics);
        } catch (RuntimeException e) {
            diagnostics.add(WorkflowDiagnostic.error(
                    "PLAN_COMPILATION_FAILED", null, "$", "执行计划编译失败: " + e.getMessage()));
            return new WorkflowCompilationResult(null, diagnostics);
        }
    }

    private void freezeSubWorkflows(WorkflowDefinitionSpec definition, WorkflowExecutionPlan plan) {
        for (var node : plan.getNodes()) {
            if (!"sub_workflow".equals(node.getType())) continue;
            if (subWorkflows == null) throw new IllegalStateException("子工作流契约解析服务不可用");
            var config = (ObjectNode) node.getConfig();
            Long tenant = com.polaris.ai.workflow.service.WorkflowSubWorkflowService.currentTenant();
            boolean pinned = "PINNED".equals(config.path("versionPolicy").asText());
            var contract = subWorkflows.resolve(config.path("definitionId").asLong(),
                    pinned ? config.path("reviewedVersionId").asText() : null, tenant);
            if (!contract.versionId().equals(config.path("reviewedVersionId").asText()))
                throw new IllegalStateException("子工作流「" + contract.name() + "」已更新，请确认新版本的输入输出后再发布");
            if (Objects.equals(contract.workflowCode(), definition.getMetadata().getCode()))
                throw new IllegalStateException("子工作流不能调用当前工作流");
            subWorkflows.assertDependencies(contract.versionId(), tenant,
                    subWorkflows.parentIds(definition.getMetadata().getCode(), tenant), 1, new int[]{0});
            config.put("workflowCode", contract.workflowCode());
            config.put("workflowVersionId", contract.versionId());
            config.put("contentHash", contract.contentHash());
            config.put("workflowName", contract.name());
            config.put("childWrites", contract.writes());
            config.set("resultSchema", contract.outputSchema());
            config.set("_inputPlan", com.polaris.ai.workflow.runtime.WorkflowSubWorkflowInputs.compile(config.get("inputs")));
            com.polaris.ai.workflow.runtime.WorkflowSubWorkflowInputs.validate(config.get("_inputPlan"), contract.inputSchema());
            validateSubWorkflowSources(config.get("_inputPlan"), definition, node.getId());
            node.setInputSchema(contract.inputSchema());
            node.setOutputSchema(subWorkflows.envelope(contract.outputSchema()));
            node.setSchemaSource("SUB_WORKFLOW_VERSION");
            node.setSchemaSourceVersion(contract.versionId());
            node.setRetryPolicy(null);
            node.setOnError("FAIL");
            // 子流程自行校验资源；不能通过扩展资源绕过其执行身份。
            node.setResourceRefs(List.of());
        }
    }

    private void validateSubWorkflowSources(JsonNode inputs, WorkflowDefinitionSpec definition, String nodeId) {
        Set<String> upstream = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(); pending.add(nodeId);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            definition.getEdges().stream().filter(e -> current.equals(e.getTarget())
                    && !"loop-return".equals(e.getTargetPort())).forEach(e -> {
                if (!nodeId.equals(e.getSource()) && upstream.add(e.getSource())) pending.add(e.getSource());
            });
        }
        boolean inLoop = definition.getNodes().stream().filter(n -> "loop".equals(n.getType())).anyMatch(loop -> {
            String start = definition.getEdges().stream().filter(e -> loop.getId().equals(e.getSource()) && "body".equals(e.getSourcePort())).map(WorkflowDefinitionSpec.Edge::getTarget).findFirst().orElse(null);
            String done = definition.getEdges().stream().filter(e -> loop.getId().equals(e.getSource()) && "done".equals(e.getSourcePort())).map(WorkflowDefinitionSpec.Edge::getTarget).findFirst().orElse(null);
            if (start == null) return false;
            Set<String> seen = new HashSet<>(); Deque<String> queue = new ArrayDeque<>(); queue.add(start);
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                if (current.equals(loop.getId()) || current.equals(done) || !seen.add(current)) continue;
                if (current.equals(nodeId)) return true;
                definition.getEdges().stream().filter(e -> current.equals(e.getSource()) && !"loop-return".equals(e.getTargetPort())).forEach(e -> queue.add(e.getTarget()));
            }
            return false;
        });
        validateSubWorkflowSourceTree(inputs, upstream, inLoop);
    }

    private void validateSubWorkflowSourceTree(JsonNode tree, Set<String> upstream, boolean inLoop) {
        if ("SOURCE".equals(tree.path("mode").asText())) {
            JsonNode ast = tree.path("ast");
            String path = ast.path("value").asText();
            if (!"path".equals(ast.path("type").asText())) throw new IllegalArgumentException("子工作流来源必须选择一个字段");
            if (path.equals("$.input") || path.startsWith("$.input.") || path.startsWith("$.input[")) return;
            if (inLoop && (path.equals("$.loop.current") || path.startsWith("$.loop.current."))) return;
            if (path.startsWith("$.nodes.")) {
                String rest = path.substring(8); int separator = rest.indexOf('.');
                if (separator > 0 && upstream.contains(rest.substring(0, separator))) {
                    String suffix = rest.substring(separator);
                    if (suffix.equals(".output") || suffix.startsWith(".output.") || suffix.startsWith(".output[")) return;
                }
            }
            throw new IllegalArgumentException("子工作流引用了不可用的上游字段：" + path);
        }
        tree.path("fields").forEach(child -> validateSubWorkflowSourceTree(child, upstream, inLoop));
        tree.path("items").forEach(child -> validateSubWorkflowSourceTree(child, upstream, inLoop));
    }

    private WorkflowExecutionPlan buildPlan(
            WorkflowDefinitionSpec definition,
            String workflowVersionId,
            Map<String, WorkflowResolvedNodeSchemaView> resolvedSchemas) {
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
                .map(node -> compileNode(node, resolvedSchemas.get(node.getId())))
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

    private WorkflowExecutionPlan.PlanNode compileNode(
            WorkflowDefinitionSpec.Node node,
            WorkflowResolvedNodeSchemaView resolvedSchema) {
        WorkflowNodeDescriptor descriptor = descriptors.find(node.getType(), node.getTypeVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "节点处理器不存在: " + node.getType() + ":" + node.getTypeVersion()));
        WorkflowExecutionPlan.PlanNode result = new WorkflowExecutionPlan.PlanNode();
        result.setId(node.getId());
        result.setType(node.getType());
        result.setHandlerVersion(descriptor.handlerVersion());
        result.setInputMapping(compileBindings(node.getInputMapping()));
        JsonNode compiledConfig = node.getConfig() == null
                ? objectMapper.createObjectNode() : node.getConfig().deepCopy();
        if ("loop".equals(node.getType())
                && "UNTIL".equals(compiledConfig.path("repeatMode").asText())
                && compiledConfig.hasNonNull("stopCondition")) {
            ((ObjectNode) compiledConfig).set("_stopConditionAst",
                    expressionParser.parse(compiledConfig.path("stopCondition").asText()));
        }
        result.setConfig(compiledConfig);
        result.setSideEffect(descriptor.sideEffect().name());
        JsonNode baseOutputSchema;
        if (resolvedSchema != null) {
            if (!Objects.equals(node.getType(), resolvedSchema.nodeType())
                    || !Objects.equals(node.getTypeVersion(), resolvedSchema.handlerVersion())) {
                throw new IllegalStateException("动态 Schema 与节点版本不一致: " + node.getId());
            }
            result.setInputSchema(copySchema(
                    resolvedSchema.inputSchema(), descriptor.inputSchema()));
            baseOutputSchema = copySchema(
                    resolvedSchema.outputSchema(), descriptor.outputSchema());
            result.setOutputSchema(baseOutputSchema);
            result.setSchemaSource(resolvedSchema.source());
            result.setSchemaSourceVersion(resolvedSchema.sourceVersion());
        } else {
            result.setInputSchema(copySchema(descriptor.inputSchema(), null));
            baseOutputSchema = copySchema(descriptor.outputSchema(), null);
            result.setOutputSchema(baseOutputSchema);
            result.setSchemaSource("NODE_CONTRACT");
            result.setSchemaSourceVersion(descriptor.handlerVersion());
        }
        if (node.getOutputSchemaOverride() != null
                && !node.getOutputSchemaOverride().isNull()) {
            String resolvedBaseHash = resolvedSchema == null
                    || resolvedSchema.fieldSources() == null
                    ? null : resolvedSchema.fieldSources().get("$baseHash");
            result.setOutputSchemaBaseHash(resolvedBaseHash == null
                    ? WorkflowOutputSchemaGovernance.fingerprint(baseOutputSchema)
                    : resolvedBaseHash);
            result.setOutputSchema(node.getOutputSchemaOverride().deepCopy());
            result.setSchemaSource("USER_OVERRIDE");
            result.setSchemaSourceVersion(WorkflowOutputSchemaGovernance.fingerprint(
                    node.getOutputSchemaOverride()));
        }
        result.setOnError(node.getOnError() == null || node.getOnError().isBlank()
                ? "FAIL" : node.getOnError());
        result.setCompensationNodeId(node.getCompensationNodeId());
        result.setTimeoutSeconds(node.getTimeoutSeconds() == null ? 120 : node.getTimeoutSeconds());
        result.setRetryPolicy(node.getRetryPolicy());
        result.setResourceRefs(node.getResourceRefs() == null ? List.of() : node.getResourceRefs());
        return result;
    }

    private Map<String, WorkflowResolvedNodeSchemaView> schemaSnapshots(
            List<WorkflowResolvedNodeSchemaView> resolvedSchemas) {
        Map<String, WorkflowResolvedNodeSchemaView> result = new LinkedHashMap<>();
        if (resolvedSchemas == null) return result;
        for (WorkflowResolvedNodeSchemaView schema : resolvedSchemas) {
            if (schema == null || schema.nodeId() == null || schema.nodeId().isBlank()) continue;
            if (result.putIfAbsent(schema.nodeId(), schema) != null) {
                throw new IllegalStateException("节点动态 Schema 重复: " + schema.nodeId());
            }
        }
        return result;
    }

    private JsonNode copySchema(JsonNode preferred, JsonNode fallback) {
        JsonNode schema = preferred == null || preferred.isNull() ? fallback : preferred;
        return schema == null ? objectMapper.createObjectNode() : schema.deepCopy();
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
