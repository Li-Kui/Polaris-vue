package com.polaris.ai.workflow.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.polaris.ai.workflow.contract.WorkflowErrorCategory;
import com.polaris.ai.workflow.contract.WorkflowErrorCode;
import com.polaris.ai.workflow.contract.WorkflowSchemaVersions;
import com.polaris.ai.workflow.definition.WorkflowDefinitionSpec;
import com.polaris.ai.workflow.definition.WorkflowDiagnostic;
import com.polaris.ai.workflow.runtime.WorkflowInputValidator;
import com.polaris.ai.workflow.runtime.WorkflowOutputSchemaGovernance;
import com.polaris.ai.workflow.runtime.WorkflowStructuredOutput;
import com.polaris.ai.workflow.spi.WorkflowNodeDescriptor;
import com.polaris.ai.workflow.spi.WorkflowNodeDescriptorResolver;

import java.util.*;

/** 工作流定义的结构和语义校验器。 */
public class WorkflowDefinitionValidator {

    private static final Set<String> EDGE_KINDS =
            Set.of("NORMAL", "CONDITION", "PARALLEL", "LOOP", "SEMANTIC");

    private final WorkflowNodeDescriptorResolver descriptors;
    private final WorkflowExpressionParser expressionParser;
    private final WorkflowInputValidator schemaValidator;

    public WorkflowDefinitionValidator(
            WorkflowNodeDescriptorResolver descriptors,
            WorkflowExpressionParser expressionParser) {
        this.descriptors = descriptors;
        this.expressionParser = expressionParser;
        this.schemaValidator = new WorkflowInputValidator();
    }

    public List<WorkflowDiagnostic> validate(WorkflowDefinitionSpec definition) {
        List<WorkflowDiagnostic> diagnostics = new ArrayList<>();
        if (definition == null) {
            diagnostics.add(error("DEFINITION_REQUIRED", null, "$", "工作流定义不能为空"));
            return diagnostics;
        }
        validateMetadata(definition, diagnostics);
        validatePolicies(definition, diagnostics);
        Map<String, WorkflowDefinitionSpec.Node> nodes = validateNodes(definition, diagnostics);
        validateCompensations(nodes, diagnostics);
        validateEdges(definition, nodes, diagnostics);
        validateOutputOverrideImpacts(definition, nodes, diagnostics);
        validateOutputBindings(definition, diagnostics);
        return diagnostics;
    }

    private void validateMetadata(
            WorkflowDefinitionSpec definition, List<WorkflowDiagnostic> diagnostics) {
        if (!WorkflowSchemaVersions.DEFINITION.equals(definition.getSchemaVersion())) {
            diagnostics.add(error("SCHEMA_VERSION_UNSUPPORTED", null, "$.schemaVersion",
                    "仅支持 WorkflowDefinition schemaVersion 2.0"));
        }
        WorkflowDefinitionSpec.Metadata metadata = definition.getMetadata();
        if (metadata == null) {
            diagnostics.add(error("METADATA_REQUIRED", null, "$.metadata", "metadata 不能为空"));
            return;
        }
        if (isBlank(metadata.getCode())
                || !metadata.getCode().matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
            diagnostics.add(error("WORKFLOW_CODE_INVALID", null, "$.metadata.code",
                    "工作流编码格式无效"));
        }
        if (isBlank(metadata.getName()) || metadata.getName().length() > 128) {
            diagnostics.add(error("WORKFLOW_NAME_INVALID", null, "$.metadata.name",
                    "工作流名称不能为空且不能超过 128 个字符"));
        }
    }

    private void validatePolicies(
            WorkflowDefinitionSpec definition, List<WorkflowDiagnostic> diagnostics) {
        WorkflowDefinitionSpec.Policies policies = definition.getPolicies();
        if (policies == null) {
            diagnostics.add(error("POLICIES_REQUIRED", null, "$.policies", "执行策略不能为空"));
            return;
        }
        range(policies.getTimeoutSeconds(), 1, 604800, "$.policies.timeoutSeconds",
                "WORKFLOW_TIMEOUT_INVALID", diagnostics);
        range(policies.getMaxNodeRuns(), 1, 10000, "$.policies.maxNodeRuns",
                "MAX_NODE_RUNS_INVALID", diagnostics);
        range(policies.getMaxParallelism(), 1, 100, "$.policies.maxParallelism",
                "MAX_PARALLELISM_INVALID", diagnostics);
        if (policies.getTokenBudget() != null && policies.getTokenBudget() < 0) {
            diagnostics.add(error("TOKEN_BUDGET_INVALID", null, "$.policies.tokenBudget",
                    "Token预算不能小于0"));
        }
        if (policies.getCostBudget() != null && policies.getCostBudget().signum() < 0) {
            diagnostics.add(error("COST_BUDGET_INVALID", null, "$.policies.costBudget",
                    "费用预算不能小于0"));
        }
    }

    private Map<String, WorkflowDefinitionSpec.Node> validateNodes(
            WorkflowDefinitionSpec definition, List<WorkflowDiagnostic> diagnostics) {
        Map<String, WorkflowDefinitionSpec.Node> result = new LinkedHashMap<>();
        List<WorkflowDefinitionSpec.Node> nodes = definition.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            diagnostics.add(error("NODES_REQUIRED", null, "$.nodes", "节点不能为空"));
            return result;
        }
        if (nodes.size() > 200) {
            diagnostics.add(error("TOO_MANY_NODES", null, "$.nodes", "节点数量不能超过200"));
        }
        for (int index = 0; index < nodes.size(); index++) {
            WorkflowDefinitionSpec.Node node = nodes.get(index);
            String path = "$.nodes[" + index + "]";
            if (node == null || isBlank(node.getId())
                    || !node.getId().matches("[A-Za-z][A-Za-z0-9_.-]{0,127}")) {
                diagnostics.add(error("NODE_ID_INVALID", null, path + ".id", "节点ID格式无效"));
                continue;
            }
            String nodeId = node.getId();
            if ("__start__".equals(nodeId) || "__end__".equals(nodeId)) {
                diagnostics.add(error("NODE_ID_RESERVED", nodeId, path + ".id",
                        "节点ID不能使用系统保留值"));
            }
            if (result.putIfAbsent(nodeId, node) != null) {
                diagnostics.add(error("NODE_ID_DUPLICATE", nodeId, path + ".id",
                        "节点ID重复"));
            }
            if (isBlank(node.getName()) || node.getName().length() > 128) {
                diagnostics.add(error("NODE_NAME_INVALID", nodeId, path + ".name",
                        "节点名称不能为空且不能超过128个字符"));
            }
            WorkflowNodeDescriptor descriptor = null;
            if (isBlank(node.getType()) || isBlank(node.getTypeVersion())) {
                diagnostics.add(error("NODE_TYPE_REQUIRED", nodeId, path + ".type",
                        "节点类型和版本不能为空"));
            } else {
                descriptor = descriptors.find(node.getType(), node.getTypeVersion()).orElse(null);
                if (descriptor == null) {
                    diagnostics.add(error("NODE_HANDLER_NOT_FOUND", nodeId, path + ".typeVersion",
                            "未注册节点处理器: " + node.getType() + ":" + node.getTypeVersion()));
                }
            }
            if (node.getConfig() == null || !node.getConfig().isObject()) {
                diagnostics.add(error("NODE_CONFIG_INVALID", nodeId, path + ".config",
                        "节点config必须是对象"));
            } else if (descriptor != null) {
                schemaValidator.validate(descriptor.configSchema(), node.getConfig(), path + ".config")
                        .forEach(message -> diagnostics.add(error(
                                "NODE_CONFIG_SCHEMA_INVALID", nodeId, path + ".config", message)));
                validateStructuredOutput(node, path, diagnostics);
                validateOutputSchemaOverride(node, descriptor, path, diagnostics);
            }
            if (node.getTimeoutSeconds() != null
                    && (node.getTimeoutSeconds() < 1 || node.getTimeoutSeconds() > 86400)) {
                diagnostics.add(error("NODE_TIMEOUT_INVALID", nodeId, path + ".timeoutSeconds",
                        "节点超时必须在1到86400秒之间"));
            }
            String onError = node.getOnError() == null || node.getOnError().isBlank()
                    ? "FAIL" : node.getOnError();
            if (!Set.of("FAIL", "SKIP").contains(onError)) {
                diagnostics.add(error("NODE_ERROR_POLICY_INVALID", nodeId,
                        path + ".onError", "节点错误策略只能是FAIL或SKIP"));
            } else if ("SKIP".equals(onError) && descriptor != null
                    && "WRITE".equals(descriptor.sideEffect().name())) {
                diagnostics.add(error("WRITE_NODE_SKIP_NOT_ALLOWED", nodeId,
                        path + ".onError", "写节点不能使用SKIP错误策略"));
            }
            validateRetry(node, path, diagnostics);
            validateLoop(node, path, diagnostics);
            validateJoin(node, path, diagnostics);
            validateDurableControlNode(node, definition.getPolicies(), path, diagnostics);
            if ("sub_workflow".equals(node.getType()) && node.getConfig() != null
                    && definition.getMetadata() != null
                    && java.util.Objects.equals(definition.getMetadata().getCode(),
                    node.getConfig().path("workflowCode").asText())) {
                diagnostics.add(error("SUB_WORKFLOW_DIRECT_RECURSION", nodeId,
                        path + ".config.workflowCode",
                        "子工作流不能直接引用当前工作流"));
            }
            validateBindings(node.getInputMapping(), nodeId, path + ".inputMapping", diagnostics);
            validateResources(node, descriptor, path, diagnostics);
        }
        return result;
    }

    private void validateStructuredOutput(
            WorkflowDefinitionSpec.Node node, String path,
            List<WorkflowDiagnostic> diagnostics) {
        if (!"llm".equals(node.getType()) || node.getConfig() == null
                || !node.getConfig().has("structuredOutputSchema")) {
            return;
        }
        WorkflowStructuredOutput.validateSchema(
                        node.getConfig().get("structuredOutputSchema"))
                .forEach(message -> diagnostics.add(error(
                        "LLM_STRUCTURED_OUTPUT_SCHEMA_INVALID", node.getId(),
                        path + ".config.structuredOutputSchema", message)));
    }

    private void validateOutputSchemaOverride(
            WorkflowDefinitionSpec.Node node,
            WorkflowNodeDescriptor descriptor,
            String path,
            List<WorkflowDiagnostic> diagnostics) {
        JsonNode override = node.getOutputSchemaOverride();
        if (override == null || override.isNull()) return;
        WorkflowOutputSchemaGovernance.validate(override)
                .forEach(message -> diagnostics.add(error(
                        "OUTPUT_SCHEMA_OVERRIDE_INVALID", node.getId(),
                        path + ".outputSchemaOverride", message)));
        WorkflowOutputSchemaGovernance.conflicts(descriptor.outputSchema(), override)
                .forEach(message -> diagnostics.add(WorkflowDiagnostic.warning(
                        "OUTPUT_SCHEMA_OVERRIDE_CONFLICT", node.getId(),
                        path + ".outputSchemaOverride", message)));
    }

    private void validateOutputOverrideImpacts(
            WorkflowDefinitionSpec definition,
            Map<String, WorkflowDefinitionSpec.Node> nodes,
            List<WorkflowDiagnostic> diagnostics) {
        for (WorkflowDefinitionSpec.Node source : nodes.values()) {
            JsonNode override = source.getOutputSchemaOverride();
            if (override == null || override.isNull()
                    || !WorkflowOutputSchemaGovernance.validate(override).isEmpty()) {
                continue;
            }
            String prefix = "$.nodes." + source.getId() + ".output";
            for (WorkflowDefinitionSpec.Node target : nodes.values()) {
                Map<String, WorkflowDefinitionSpec.ValueBinding> mappings =
                        target.getInputMapping() == null ? Map.of() : target.getInputMapping();
                mappings.forEach((key, binding) -> {
                    String expression = binding == null ? null : binding.getExpression();
                    if (expression == null || !expression.startsWith(prefix)) return;
                    String remainder = expression.substring(prefix.length());
                    if (remainder.startsWith(".")) remainder = remainder.substring(1);
                    if (remainder.isBlank()) return;
                    List<String> segments = Arrays.stream(remainder.split("\\."))
                            .map(segment -> segment.replaceAll("\\[[0-9]+]$", ""))
                            .filter(segment -> !segment.isBlank())
                            .toList();
                    if (!WorkflowOutputSchemaGovernance.containsPath(override, segments)) {
                        diagnostics.add(WorkflowDiagnostic.warning(
                                "OUTPUT_SCHEMA_OVERRIDE_BREAKING_REFERENCE", target.getId(),
                                "$.nodes." + target.getId() + ".inputMapping." + key,
                                "映射引用的正式输出路径已不存在: " + expression));
                    }
                });
            }
            Map<String, WorkflowDefinitionSpec.ValueBinding> workflowOutputs =
                    definition.getOutputs() == null ? Map.of() : definition.getOutputs();
            workflowOutputs.forEach((key, binding) -> {
                String expression = binding == null ? null : binding.getExpression();
                if (expression == null || !expression.startsWith(prefix)) return;
                String remainder = expression.substring(prefix.length());
                if (remainder.startsWith(".")) remainder = remainder.substring(1);
                if (remainder.isBlank()) return;
                List<String> segments = Arrays.stream(remainder.split("\\."))
                        .map(segment -> segment.replaceAll("\\[[0-9]+]$", ""))
                        .filter(segment -> !segment.isBlank())
                        .toList();
                if (!WorkflowOutputSchemaGovernance.containsPath(override, segments)) {
                    diagnostics.add(WorkflowDiagnostic.warning(
                            "OUTPUT_SCHEMA_OVERRIDE_BREAKING_REFERENCE", source.getId(),
                            "$.outputs." + key,
                            "工作流输出引用的正式路径已不存在: " + expression));
                }
            });
        }
    }

    private void validateLoop(
            WorkflowDefinitionSpec.Node node, String path,
            List<WorkflowDiagnostic> diagnostics) {
        if (!"loop".equals(node.getType())) {
            return;
        }
        JsonNode maxIterations = node.getConfig() == null
                ? null : node.getConfig().get("maxIterations");
        if (maxIterations == null || !maxIterations.canConvertToInt()
                || maxIterations.intValue() < 1 || maxIterations.intValue() > 1000) {
            diagnostics.add(error("LOOP_MAX_ITERATIONS_INVALID", node.getId(),
                    path + ".config.maxIterations", "循环节点必须配置1到1000之间的最大迭代次数"));
        }
    }

    private void validateJoin(
            WorkflowDefinitionSpec.Node node, String path,
            List<WorkflowDiagnostic> diagnostics) {
        if (!"join".equals(node.getType())) {
            return;
        }
        String mode = node.getConfig() == null
                ? null : node.getConfig().path("mode").asText(null);
        if (mode == null || !Set.of("ANY", "ALL", "N_OF_M").contains(mode)) {
            diagnostics.add(error("JOIN_MODE_INVALID", node.getId(),
                    path + ".config.mode", "汇聚节点模式必须明确配置为ANY、ALL或N_OF_M"));
            return;
        }
        if ("N_OF_M".equals(mode)
                && node.getConfig().path("requiredBranches").asInt(0) < 1) {
            diagnostics.add(error("JOIN_THRESHOLD_INVALID", node.getId(),
                    path + ".config.requiredBranches",
                    "N_OF_M汇聚必须配置大于0的所需分支数"));
        }
    }

    private void validateDurableControlNode(
            WorkflowDefinitionSpec.Node node,
            WorkflowDefinitionSpec.Policies policies,
            String path,
            List<WorkflowDiagnostic> diagnostics) {
        if (node.getConfig() == null || policies == null
                || policies.getTimeoutSeconds() == null) {
            return;
        }
        if ("wait".equals(node.getType())) {
            int delaySeconds = node.getConfig().path("delaySeconds").asInt(0);
            if (delaySeconds >= policies.getTimeoutSeconds()) {
                diagnostics.add(error("WAIT_EXCEEDS_WORKFLOW_TIMEOUT", node.getId(),
                        path + ".config.delaySeconds",
                        "等待时长必须小于工作流总超时"));
            }
        }
        if ("approval".equals(node.getType())) {
            int approvalTimeout = node.getConfig().path("timeoutSeconds").asInt(86400);
            if (approvalTimeout >= policies.getTimeoutSeconds()) {
                diagnostics.add(error("APPROVAL_EXCEEDS_WORKFLOW_TIMEOUT", node.getId(),
                        path + ".config.timeoutSeconds",
                        "审批时限必须小于工作流总超时"));
            }
            if ("N_OF_M".equals(node.getConfig().path("approvalMode").asText())) {
                int required = node.getConfig().path("requiredApprovals").asInt(0);
                int assignees = node.getConfig().path("assigneeIds").size();
                if (required < 1 || required > assignees) {
                    diagnostics.add(error("APPROVAL_THRESHOLD_INVALID", node.getId(),
                            path + ".config.requiredApprovals",
                            "N_OF_M审批阈值必须在1和审批人数量之间"));
                }
            }
        }
    }

    private void validateRetry(
            WorkflowDefinitionSpec.Node node, String path,
            List<WorkflowDiagnostic> diagnostics) {
        WorkflowDefinitionSpec.RetryPolicy retry = node.getRetryPolicy();
        if (retry == null) {
            return;
        }
        if (retry.getMaxAttempts() == null
                || retry.getMaxAttempts() < 1 || retry.getMaxAttempts() > 10) {
            diagnostics.add(error("RETRY_ATTEMPTS_INVALID", node.getId(),
                    path + ".retryPolicy.maxAttempts", "最大尝试次数必须在1到10之间"));
        }
        if (!Set.of("FIXED", "EXPONENTIAL").contains(retry.getBackoff())) {
            diagnostics.add(error("RETRY_BACKOFF_INVALID", node.getId(),
                    path + ".retryPolicy.backoff", "重试退避类型无效"));
        }
        if (retry.getInitialDelayMs() == null || retry.getInitialDelayMs() < 0
                || retry.getMaxDelayMs() == null
                || retry.getMaxDelayMs() < retry.getInitialDelayMs()
                || retry.getMaxDelayMs() > 300000) {
            diagnostics.add(error("RETRY_DELAY_INVALID", node.getId(),
                    path + ".retryPolicy", "重试延时不能为空，最大延时不能小于初始延时且不能超过5分钟"));
        }
        if (retry.getRetryableErrors() != null) {
            Set<String> allowed = new HashSet<>();
            for (WorkflowErrorCode value : WorkflowErrorCode.values()) {
                allowed.add(value.name());
            }
            for (WorkflowErrorCategory value : WorkflowErrorCategory.values()) {
                allowed.add(value.name());
            }
            retry.getRetryableErrors().stream()
                    .filter(value -> value == null || !allowed.contains(value))
                    .forEach(value -> diagnostics.add(error(
                            "RETRY_ERROR_CODE_INVALID", node.getId(),
                            path + ".retryPolicy.retryableErrors",
                            "可重试错误类型无效: " + value)));
        }
    }

    private void validateResources(
            WorkflowDefinitionSpec.Node node,
            WorkflowNodeDescriptor descriptor,
            String path,
            List<WorkflowDiagnostic> diagnostics) {
        Set<String> keys = new HashSet<>();
        List<WorkflowDefinitionSpec.ResourceRef> refs = node.getResourceRefs();
        if (refs == null) {
            refs = List.of();
        }
        for (int index = 0; index < refs.size(); index++) {
            WorkflowDefinitionSpec.ResourceRef ref = refs.get(index);
            String refPath = path + ".resourceRefs[" + index + "]";
            if (ref == null || isBlank(ref.getKind()) || isBlank(ref.getKey())) {
                diagnostics.add(error("RESOURCE_REF_INVALID", node.getId(), refPath,
                        "资源类型和逻辑键不能为空"));
                continue;
            }
            if (!ref.getKind().matches("[A-Z][A-Z0-9_]{0,63}")) {
                diagnostics.add(error("RESOURCE_KIND_INVALID", node.getId(), refPath + ".kind",
                        "资源类型必须使用大写稳定编码"));
            }
            if (!keys.add(ref.getKind() + ":" + ref.getKey())) {
                diagnostics.add(error("RESOURCE_REF_DUPLICATE", node.getId(), refPath,
                        "节点存在重复资源引用"));
            }
        }
        if (descriptor != null) {
            Set<String> actualKinds = refs.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(WorkflowDefinitionSpec.ResourceRef::getKind)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            descriptor.requiredResourceKinds().stream()
                    .filter(kind -> !actualKinds.contains(kind))
                    .forEach(kind -> diagnostics.add(error(
                            "NODE_RESOURCE_REQUIRED", node.getId(), path + ".resourceRefs",
                            "节点缺少必需资源类型: " + kind)));
        }
    }

    private void validateEdges(
            WorkflowDefinitionSpec definition,
            Map<String, WorkflowDefinitionSpec.Node> nodes,
            List<WorkflowDiagnostic> diagnostics) {
        List<WorkflowDefinitionSpec.Edge> edges = definition.getEdges();
        if (edges == null || edges.isEmpty()) {
            diagnostics.add(error("EDGES_REQUIRED", null, "$.edges", "连线不能为空"));
            return;
        }
        if (edges.size() > 1000) {
            diagnostics.add(error("TOO_MANY_EDGES", null, "$.edges", "连线数量不能超过1000"));
        }
        Set<String> validIds = new HashSet<>(nodes.keySet());
        Set<String> compensationTargets = compensationTargets(nodes);
        validIds.add("__start__");
        validIds.add("__end__");
        Set<String> edgeKeys = new HashSet<>();
        Map<String, List<WorkflowDefinitionSpec.Edge>> outgoing = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, List<String>> reverse = new HashMap<>();
        for (int index = 0; index < edges.size(); index++) {
            WorkflowDefinitionSpec.Edge edge = edges.get(index);
            String path = "$.edges[" + index + "]";
            if (edge == null) {
                diagnostics.add(error("EDGE_INVALID", null, path, "连线不能为空"));
                continue;
            }
            if (!validIds.contains(edge.getSource()) || !validIds.contains(edge.getTarget())) {
                diagnostics.add(error("EDGE_ENDPOINT_INVALID", edge.getSource(), path,
                        "连线引用了不存在的节点"));
                continue;
            }
            if ("__end__".equals(edge.getSource()) || "__start__".equals(edge.getTarget())) {
                diagnostics.add(error("EDGE_DIRECTION_INVALID", edge.getSource(), path,
                        "开始和结束节点的连线方向无效"));
            }
            if (compensationTargets.contains(edge.getSource())
                    || compensationTargets.contains(edge.getTarget())) {
                diagnostics.add(error("COMPENSATION_EDGE_NOT_ALLOWED", edge.getSource(), path,
                        "补偿节点不能参与主执行图连线"));
            }
            if (!EDGE_KINDS.contains(edge.getKind())) {
                diagnostics.add(error("EDGE_KIND_INVALID", edge.getSource(), path + ".kind",
                        "连线类型无效"));
            }
            String edgeKey = edge.getSource() + "\u0000" + edge.getTarget() + "\u0000"
                    + String.valueOf(edge.getSourcePort()) + "\u0000" + String.valueOf(edge.getKind());
            if (!edgeKeys.add(edgeKey)) {
                diagnostics.add(error("EDGE_DUPLICATE", edge.getSource(), path, "存在重复连线"));
            }
            if ("CONDITION".equals(edge.getKind()) && !Boolean.TRUE.equals(edge.getDefaultEdge())) {
                validateCondition(edge, path, diagnostics);
            } else if (edge.getCondition() != null) {
                diagnostics.add(error("EDGE_CONDITION_NOT_ALLOWED", edge.getSource(), path + ".condition",
                        "只有非默认条件连线可以配置表达式"));
            }
            outgoing.computeIfAbsent(edge.getSource(), ignored -> new ArrayList<>()).add(edge);
            adjacency.computeIfAbsent(edge.getSource(), ignored -> new ArrayList<>()).add(edge.getTarget());
            reverse.computeIfAbsent(edge.getTarget(), ignored -> new ArrayList<>()).add(edge.getSource());
        }
        validateStartAndEnd(outgoing, reverse, diagnostics);
        for (Map.Entry<String, WorkflowDefinitionSpec.Node> entry : nodes.entrySet()) {
            if (compensationTargets.contains(entry.getKey())) {
                continue;
            }
            validateOutgoing(entry.getValue(), outgoing.getOrDefault(entry.getKey(), List.of()), diagnostics);
            validateJoinIncoming(entry.getValue(),
                    reverse.getOrDefault(entry.getKey(), List.of()).size(), diagnostics);
        }
        validateReachability(nodes.keySet(), compensationTargets, adjacency, reverse, diagnostics);
        validateAcyclicWithoutLoopEdges(edges, validIds, diagnostics);
    }

    private void validateJoinIncoming(
            WorkflowDefinitionSpec.Node node,
            int incomingCount,
            List<WorkflowDiagnostic> diagnostics) {
        if (!"join".equals(node.getType()) || node.getConfig() == null) {
            return;
        }
        if (incomingCount < 2) {
            diagnostics.add(error("JOIN_INCOMING_INVALID", node.getId(),
                    "$.nodes[" + node.getId() + "]",
                    "汇聚节点至少需要两个入口"));
            return;
        }
        if ("N_OF_M".equals(node.getConfig().path("mode").asText())) {
            int required = node.getConfig().path("requiredBranches").asInt(0);
            if (required > incomingCount) {
                diagnostics.add(error("JOIN_THRESHOLD_INVALID", node.getId(),
                        "$.nodes[" + node.getId() + "].config.requiredBranches",
                        "N_OF_M汇聚所需分支数不能超过入口数量"));
            }
        }
    }

    private void validateCondition(
            WorkflowDefinitionSpec.Edge edge, String path,
            List<WorkflowDiagnostic> diagnostics) {
        WorkflowDefinitionSpec.Condition condition = edge.getCondition();
        if (condition == null || isBlank(condition.getExpression())) {
            diagnostics.add(error("CONDITION_REQUIRED", edge.getSource(), path + ".condition",
                    "非默认条件连线必须配置表达式"));
            return;
        }
        if (condition.getPriority() == null || condition.getPriority() < 0
                || condition.getPriority() > 10000) {
            diagnostics.add(error("CONDITION_PRIORITY_INVALID", edge.getSource(),
                    path + ".condition.priority", "条件优先级必须在0到10000之间"));
        }
        if (!Set.of("FAIL", "FALSE").contains(condition.getOnError())) {
            diagnostics.add(error("CONDITION_ERROR_POLICY_INVALID", edge.getSource(),
                    path + ".condition.onError", "条件异常策略只能是FAIL或FALSE"));
        }
        try {
            expressionParser.parse(condition.getExpression());
        } catch (IllegalArgumentException e) {
            diagnostics.add(error("EXPRESSION_INVALID", edge.getSource(),
                    path + ".condition.expression", e.getMessage()));
        }
    }

    private void validateStartAndEnd(
            Map<String, List<WorkflowDefinitionSpec.Edge>> outgoing,
            Map<String, List<String>> reverse,
            List<WorkflowDiagnostic> diagnostics) {
        List<WorkflowDefinitionSpec.Edge> startEdges = outgoing.getOrDefault("__start__", List.of());
        if (startEdges.size() != 1 || !"NORMAL".equals(startEdges.get(0).getKind())) {
            diagnostics.add(error("START_EDGE_INVALID", null, "$.edges",
                    "工作流必须且只能有一条从__start__出发的普通连线"));
        }
        if (!reverse.containsKey("__end__")) {
            diagnostics.add(error("END_EDGE_REQUIRED", null, "$.edges",
                    "工作流必须存在指向__end__的连线"));
        }
    }

    private void validateOutgoing(
            WorkflowDefinitionSpec.Node node,
            List<WorkflowDefinitionSpec.Edge> outgoing,
            List<WorkflowDiagnostic> diagnostics) {
        String path = "$.nodes[" + node.getId() + "]";
        if ("condition".equals(node.getType())) {
            long defaults = outgoing.stream()
                    .filter(item -> Boolean.TRUE.equals(item.getDefaultEdge())).count();
            Set<Integer> priorities = new HashSet<>();
            boolean duplicatePriority = outgoing.stream()
                    .filter(item -> !Boolean.TRUE.equals(item.getDefaultEdge()))
                    .map(WorkflowDefinitionSpec.Edge::getCondition)
                    .filter(java.util.Objects::nonNull)
                    .map(WorkflowDefinitionSpec.Condition::getPriority)
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(priority -> !priorities.add(priority));
            if (outgoing.size() < 2 || outgoing.stream()
                    .anyMatch(item -> !"CONDITION".equals(item.getKind())) || defaults != 1) {
                diagnostics.add(error("CONDITION_BRANCH_INVALID", node.getId(), path,
                        "条件节点至少需要两个条件出口，并且必须且只能有一个默认出口"));
            }
            if (duplicatePriority) {
                diagnostics.add(error("CONDITION_PRIORITY_DUPLICATE", node.getId(), path,
                        "同一条件节点的非默认分支优先级不能重复"));
            }
            return;
        }
        if ("parallel".equals(node.getType())) {
            if (outgoing.size() < 2 || outgoing.stream()
                    .anyMatch(item -> !"PARALLEL".equals(item.getKind()))) {
                diagnostics.add(error("PARALLEL_BRANCH_INVALID", node.getId(), path,
                        "并行节点至少需要两个PARALLEL出口"));
            }
            return;
        }
        if ("llm_classifier".equals(node.getType())) {
            Set<String> configured = new LinkedHashSet<>();
            Set<String> labels = new LinkedHashSet<>();
            boolean duplicateSlug = false;
            boolean duplicateLabel = false;
            if (node.getConfig() != null) {
                for (JsonNode item : node.getConfig().path("branches")) {
                    String slug = item.path("slug").asText();
                    String label = item.path("label").asText(slug).trim();
                    if (!configured.add(slug)) duplicateSlug = true;
                    if (!labels.add(label)) duplicateLabel = true;
                }
            }
            if (duplicateSlug || duplicateLabel) {
                diagnostics.add(error("CLASSIFIER_CATEGORY_DUPLICATE", node.getId(), path,
                        duplicateSlug ? "分类内部标识重复，请删除后重新添加该分类"
                                : "分类名称不能重复"));
            }
            String fallbackSlug = node.getConfig() == null ? ""
                    : node.getConfig().path("fallbackSlug").asText();
            if (fallbackSlug.isBlank() && !configured.isEmpty()) {
                fallbackSlug = configured.stream().reduce((first, second) -> second).orElse("");
            }
            String strategy = node.getConfig() == null ? "FALLBACK"
                    : node.getConfig().path("invalidResponseStrategy").asText("FALLBACK");
            if (("FALLBACK".equals(strategy) || !fallbackSlug.isBlank())
                    && !configured.contains(fallbackSlug)) {
                diagnostics.add(error("CLASSIFIER_FALLBACK_INVALID", node.getId(), path,
                        "兜底分类已失效，请重新选择"));
            }
            Set<String> ports = new LinkedHashSet<>();
            outgoing.forEach(item -> ports.add(item.getSourcePort()));
            if (outgoing.size() < 2 || outgoing.stream()
                    .anyMatch(item -> !"SEMANTIC".equals(item.getKind())
                            || isBlank(item.getSourcePort()))
                    || ports.size() != outgoing.size()
                    || !ports.equals(configured)) {
                diagnostics.add(error("CLASSIFIER_BRANCH_INVALID", node.getId(), path,
                        "每个分类结果都必须选择且只能选择一个下一节点"));
            }
            return;
        }
        if ("loop".equals(node.getType())) {
            long loopEdges = outgoing.stream().filter(item -> "LOOP".equals(item.getKind())).count();
            long exitEdges = outgoing.stream().filter(item -> "CONDITION".equals(item.getKind())
                    && Boolean.TRUE.equals(item.getDefaultEdge())).count();
            if (loopEdges != 1 || exitEdges != 1 || outgoing.size() != 2) {
                diagnostics.add(error("LOOP_BRANCH_INVALID", node.getId(), path,
                        "循环节点必须包含一条LOOP回边和一条默认退出边"));
            }
            return;
        }
        if (outgoing.size() != 1 || !"NORMAL".equals(outgoing.get(0).getKind())) {
            diagnostics.add(error("NODE_OUTGOING_INVALID", node.getId(), path,
                    "普通节点必须且只能有一条NORMAL出口"));
        }
    }

    private void validateReachability(
            Set<String> nodeIds,
            Set<String> compensationTargets,
            Map<String, List<String>> adjacency,
            Map<String, List<String>> reverse,
            List<WorkflowDiagnostic> diagnostics) {
        Set<String> reachable = traverse("__start__", adjacency);
        Set<String> canReachEnd = traverse("__end__", reverse);
        for (String nodeId : nodeIds) {
            if (compensationTargets.contains(nodeId)) {
                continue;
            }
            if (!reachable.contains(nodeId)) {
                diagnostics.add(error("NODE_UNREACHABLE", nodeId, "$.nodes[" + nodeId + "]",
                        "节点从开始节点不可达"));
            }
            if (!canReachEnd.contains(nodeId)) {
                diagnostics.add(error("NODE_CANNOT_REACH_END", nodeId,
                        "$.nodes[" + nodeId + "]", "节点无法到达结束节点"));
            }
        }
    }

    private void validateCompensations(
            Map<String, WorkflowDefinitionSpec.Node> nodes,
            List<WorkflowDiagnostic> diagnostics) {
        Set<String> targets = compensationTargets(nodes);
        for (WorkflowDefinitionSpec.Node node : nodes.values()) {
            String compensationNodeId = node.getCompensationNodeId();
            if (isBlank(compensationNodeId)) {
                continue;
            }
            String path = "$.nodes[" + node.getId() + "].compensationNodeId";
            WorkflowDefinitionSpec.Node compensation = nodes.get(compensationNodeId);
            if (compensation == null) {
                diagnostics.add(error("COMPENSATION_NODE_NOT_FOUND", node.getId(), path,
                        "补偿节点不存在"));
                continue;
            }
            if (node.getId().equals(compensationNodeId)) {
                diagnostics.add(error("COMPENSATION_SELF_REFERENCE", node.getId(), path,
                        "节点不能补偿自身"));
            }
            WorkflowNodeDescriptor sourceDescriptor = descriptors.find(
                    node.getType(), node.getTypeVersion()).orElse(null);
            WorkflowNodeDescriptor targetDescriptor = descriptors.find(
                    compensation.getType(), compensation.getTypeVersion()).orElse(null);
            if (sourceDescriptor != null
                    && !"WRITE".equals(sourceDescriptor.sideEffect().name())) {
                diagnostics.add(error("COMPENSATION_SOURCE_NOT_WRITE", node.getId(), path,
                        "只有外部写副作用节点可以配置补偿节点"));
            }
            if (targetDescriptor != null
                    && !"WRITE".equals(targetDescriptor.sideEffect().name())) {
                diagnostics.add(error("COMPENSATION_TARGET_NOT_WRITE", node.getId(), path,
                        "补偿节点必须声明WRITE副作用"));
            }
            if (!isBlank(compensation.getCompensationNodeId())) {
                diagnostics.add(error("COMPENSATION_CHAIN_NOT_ALLOWED", compensationNodeId,
                        "$.nodes[" + compensationNodeId + "].compensationNodeId",
                        "补偿节点不能继续配置补偿链"));
            }
            if (Set.of("approval", "wait", "condition", "parallel", "join", "loop")
                    .contains(compensation.getType())) {
                diagnostics.add(error("COMPENSATION_CONTROL_NODE_NOT_ALLOWED",
                        compensationNodeId, path, "控制节点不能作为补偿节点"));
            }
        }
        for (String target : targets) {
            if (nodes.values().stream().noneMatch(
                    node -> target.equals(node.getCompensationNodeId()))) {
                diagnostics.add(error("COMPENSATION_TARGET_UNUSED", target,
                        "$.nodes[" + target + "]", "补偿节点没有被任何写节点引用"));
            }
        }
    }

    private Set<String> compensationTargets(
            Map<String, WorkflowDefinitionSpec.Node> nodes) {
        Set<String> targets = new LinkedHashSet<>();
        nodes.values().stream()
                .map(WorkflowDefinitionSpec.Node::getCompensationNodeId)
                .filter(value -> !isBlank(value))
                .forEach(targets::add);
        return targets;
    }

    private void validateAcyclicWithoutLoopEdges(
            List<WorkflowDefinitionSpec.Edge> edges,
            Set<String> allIds,
            List<WorkflowDiagnostic> diagnostics) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        for (String id : allIds) {
            indegree.put(id, 0);
        }
        for (WorkflowDefinitionSpec.Edge edge : edges) {
            if (edge == null || "LOOP".equals(edge.getKind())
                    || !allIds.contains(edge.getSource()) || !allIds.contains(edge.getTarget())) {
                continue;
            }
            adjacency.computeIfAbsent(edge.getSource(), ignored -> new ArrayList<>())
                    .add(edge.getTarget());
            indegree.computeIfPresent(edge.getTarget(), (key, value) -> value + 1);
        }
        Deque<String> queue = new ArrayDeque<>();
        indegree.forEach((key, value) -> {
            if (value == 0) queue.addLast(key);
        });
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            visited++;
            for (String next : adjacency.getOrDefault(current, List.of())) {
                int value = indegree.computeIfPresent(next, (key, old) -> old - 1);
                if (value == 0) queue.addLast(next);
            }
        }
        if (visited != indegree.size()) {
            diagnostics.add(error("UNCONTROLLED_CYCLE", null, "$.edges",
                    "普通、条件或并行连线形成了未受LOOP节点控制的环"));
        }
    }

    private void validateOutputBindings(
            WorkflowDefinitionSpec definition, List<WorkflowDiagnostic> diagnostics) {
        validateBindings(definition.getOutputs(), null, "$.outputs", diagnostics);
    }

    private void validateBindings(
            Map<String, WorkflowDefinitionSpec.ValueBinding> bindings,
            String nodeId, String path,
            List<WorkflowDiagnostic> diagnostics) {
        if (bindings == null) {
            return;
        }
        bindings.forEach((key, binding) -> {
            if (binding == null || isBlank(key)) {
                diagnostics.add(error("VALUE_BINDING_INVALID", nodeId, path,
                        "映射键和值不能为空"));
                return;
            }
            boolean expression = !isBlank(binding.getExpression());
            boolean literal = binding.getValue() != null;
            if (expression == literal) {
                diagnostics.add(error("VALUE_BINDING_AMBIGUOUS", nodeId, path + "." + key,
                        "映射必须且只能配置expression或value之一"));
            } else if (expression) {
                try {
                    expressionParser.parse(binding.getExpression());
                } catch (IllegalArgumentException e) {
                    diagnostics.add(error("EXPRESSION_INVALID", nodeId,
                            path + "." + key + ".expression", e.getMessage()));
                }
            }
        });
    }

    private Set<String> traverse(String start, Map<String, List<String>> graph) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            queue.addAll(graph.getOrDefault(current, List.of()));
        }
        return visited;
    }

    private void range(
            Integer value, int min, int max, String fieldPath, String code,
            List<WorkflowDiagnostic> diagnostics) {
        if (value == null || value < min || value > max) {
            diagnostics.add(error(code, null, fieldPath,
                    "数值必须在" + min + "到" + max + "之间"));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private WorkflowDiagnostic error(
            String code, String nodeId, String fieldPath, String message) {
        return WorkflowDiagnostic.error(code, nodeId, fieldPath, message);
    }
}
