package com.polaris.ai.workflow.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.workflow.contract.WorkflowErrorCode;
import com.polaris.ai.workflow.definition.WorkflowExecutionPlan;
import com.polaris.ai.workflow.domain.*;
import com.polaris.ai.workflow.mapper.*;
import com.polaris.ai.workflow.registry.WorkflowNodeRegistry;
import com.polaris.ai.workflow.security.WorkflowDataRedactor;
import com.polaris.ai.workflow.service.WorkflowExecutionService;
import com.polaris.ai.workflow.spi.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 由数据库隔离令牌保护的单次持久化图执行器。 */
@Slf4j
@Component
public class WorkflowExecutionEngine {

    private static final int MAX_INLINE_OUTPUT_BYTES = 1024 * 1024;
    private static final WorkflowInputValidator OUTPUT_VALIDATOR = new WorkflowInputValidator();

    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowVersionMapper versionMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;
    private final WorkflowCheckpointMapper checkpointMapper;
    private final WorkflowApprovalTaskMapper approvalTaskMapper;
    private final WorkflowExecutionPersistence persistence;
    private final WorkflowNodeRegistry nodeRegistry;
    private final WorkflowResourceResolver resourceResolver;
    private final WorkflowExpressionEvaluator expressionEvaluator;
    private final WorkflowExecutionService executionService;
    private final ThreadPoolTaskExecutor nodeExecutor;
    private final ObjectMapper objectMapper;
    private final WorkflowDataRedactor dataRedactor;

    public WorkflowExecutionEngine(
            WorkflowExecutionMapper executionMapper,
            WorkflowVersionMapper versionMapper,
            WorkflowNodeRunMapper nodeRunMapper,
            WorkflowCheckpointMapper checkpointMapper,
            WorkflowApprovalTaskMapper approvalTaskMapper,
            WorkflowExecutionPersistence persistence,
            WorkflowNodeRegistry nodeRegistry,
            WorkflowResourceResolver resourceResolver,
            WorkflowExpressionEvaluator expressionEvaluator,
            WorkflowExecutionService executionService,
            @Qualifier("workflowNodeTaskExecutor") ThreadPoolTaskExecutor nodeExecutor,
            ObjectMapper objectMapper,
            WorkflowDataRedactor dataRedactor) {
        this.executionMapper = executionMapper;
        this.versionMapper = versionMapper;
        this.nodeRunMapper = nodeRunMapper;
        this.checkpointMapper = checkpointMapper;
        this.approvalTaskMapper = approvalTaskMapper;
        this.persistence = persistence;
        this.nodeRegistry = nodeRegistry;
        this.resourceResolver = resourceResolver;
        this.expressionEvaluator = expressionEvaluator;
        this.executionService = executionService;
        this.nodeExecutor = nodeExecutor;
        this.objectMapper = objectMapper;
        this.dataRedactor = dataRedactor;
    }

    public void execute(String executionId, String runnerId, long fencingToken, int leaseSeconds) {
        WorkflowExecution execution = null;
        WorkflowExecutionPlan plan = null;
        WorkflowRuntimeState state = null;
        Map<String, ResolvedWorkflowResource> resources = Map.of();
        try {
            execution = requireClaim(executionId, runnerId, fencingToken);
            WorkflowVersion version = versionMapper.selectByVersionId(
                    execution.getWorkflowVersionId());
            if (version == null) {
                throw new NodeFailure(WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                        "执行绑定的不可变版本不存在", false);
            }
            plan = readPlan(version.getExecutionPlanJson());
            if (!execution.getPlanHash().equals(plan.getContentHash())) {
                throw new NodeFailure(WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                        "检查点与执行计划哈希不一致", false);
            }
            if (execution.getRecoveryCount() != null && execution.getRecoveryCount() > 0
                    && hasUnconfirmedWrite(executionId)) {
                persistence.needsAttention(executionId, runnerId, fencingToken,
                        WorkflowErrorCode.SIDE_EFFECT_UNCONFIRMED.name(),
                        "写节点在检查点提交前失去租约，需要人工确认外部副作用");
                return;
            }
            persistence.appendEvent(executionId, runnerId, fencingToken,
                    execution.getRecoveryCount() != null && execution.getRecoveryCount() > 0
                            ? "EXECUTION_RECOVERING" : "EXECUTION_STARTED",
                    null, null, Map.of("fencingToken", fencingToken));
            WorkflowResourceResolver.Resolution resolution = resourceResolver.resolveSnapshot(
                    plan,
                    execution.getTenantId(),
                    execution.getEnvironment(),
                    execution.getPrincipalType(),
                    execution.getPrincipalId(),
                    execution.getBindingSnapshot());
            resources = resolution.resources();
            state = restoreState(execution, plan);
            runPlan(execution, plan, state, resources,
                    runnerId, fencingToken, leaseSeconds);
        } catch (LeaseLostException e) {
            log.warn("Workflow execution {} stopped because its lease was lost", executionId);
        } catch (NodeFailure e) {
            NodeFailure failure = e;
            if (execution != null && plan != null && state != null
                    && !state.getCompensations().isEmpty()) {
                try {
                    if (!runCompensations(
                            execution, plan, state, resources, runnerId,
                            fencingToken, leaseSeconds, e)) {
                        failure = new NodeFailure(
                                WorkflowErrorCode.SIDE_EFFECT_UNCONFIRMED.name(),
                                "工作流失败且至少一个补偿操作未能确认，需要人工处置", true);
                    }
                } catch (LeaseLostException leaseLost) {
                    log.warn("Workflow execution {} lost its lease during compensation", executionId);
                    return;
                } catch (Exception compensationError) {
                    log.error("Workflow execution {} compensation failed", executionId,
                            compensationError);
                    failure = new NodeFailure(
                            WorkflowErrorCode.SIDE_EFFECT_UNCONFIRMED.name(),
                            "工作流补偿执行异常，需要人工处置", true);
                }
            }
            finishFailure(executionId, runnerId, fencingToken, failure);
        } catch (Exception e) {
            log.error("Workflow execution {} failed", executionId, e);
            finishFailure(executionId, runnerId, fencingToken,
                    new NodeFailure(WorkflowErrorCode.INTERNAL_ERROR.name(),
                            safeMessage(e), false));
        }
    }

    private void runPlan(
            WorkflowExecution execution,
            WorkflowExecutionPlan plan,
            WorkflowRuntimeState state,
            Map<String, ResolvedWorkflowResource> resources,
            String runnerId,
            long fencingToken,
            int leaseSeconds) throws Exception {
        Map<String, WorkflowExecutionPlan.PlanNode> nodes = new HashMap<>();
        plan.getNodes().forEach(node -> nodes.put(node.getId(), node));
        Map<String, List<WorkflowExecutionPlan.PlanEdge>> outgoing = outgoing(plan);
        Map<String, Integer> incomingCounts = incomingCounts(plan);
        int maxNodeRuns = intPolicy(plan, "maxNodeRuns", 200);
        long workflowDeadline = workflowStartedAtMillis(execution)
                + TimeUnit.SECONDS.toMillis(intPolicy(plan, "timeoutSeconds", 1800));
        WorkflowCancellation cancellation = () -> cancellationRequested(
                execution.getExecutionId(), runnerId, fencingToken);
        while (!state.getPending().isEmpty()) {
            ensureWorkflowDeadline(workflowDeadline);
            ensureLease(execution.getExecutionId(), runnerId, fencingToken, leaseSeconds);
            if (cancellation.isCancellationRequested()) {
                throw new NodeFailure(WorkflowErrorCode.NODE_CANCELLED.name(),
                        "工作流执行已取消", false);
            }
            List<WorkflowRuntimeState.Token> parallelBatch = takeParallelBatch(
                    state, nodes, intPolicy(plan, "maxParallelism", 1));
            if (parallelBatch.size() > 1) {
                boolean suspended = executeParallelBatch(
                        execution, plan, state, parallelBatch, nodes, outgoing,
                        resources, cancellation, runnerId, fencingToken,
                        leaseSeconds, workflowDeadline, maxNodeRuns);
                if (suspended) {
                    return;
                }
                continue;
            }
            WorkflowRuntimeState.Token token = state.getPending().remove(0);
            if ("__end__".equals(token.getNodeId())) {
                state.setReachedEnd(true);
                continue;
            }
            WorkflowExecutionPlan.PlanNode node = nodes.get(token.getNodeId());
            if (node == null) {
                throw new NodeFailure(WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                        "执行计划引用了不存在的节点", false);
            }
            String instanceKey = node.getId() + "@" + token.getBranchPath();
            if (state.getCompletedKeys().contains(instanceKey)) {
                continue;
            }
            if ("join".equals(node.getType())) {
                int arrivals = state.getArrivals().getOrDefault(instanceKey, 0);
                int requiredArrivals = requiredJoinArrivals(
                        node, incomingCounts.getOrDefault(node.getId(), 1));
                if (arrivals < requiredArrivals) {
                    continue;
                }
            }
            state.setTotalNodeRuns(state.getTotalNodeRuns() + 1);
            if (state.getTotalNodeRuns() > maxNodeRuns) {
                throw new NodeFailure(WorkflowErrorCode.QUOTA_EXCEEDED.name(),
                        "节点运行次数超过工作流上限", false);
            }
            NodeExecution nodeExecution;
            try {
                JsonNode contextRoot = contextRoot(execution, state, token.getBranchPath());
                JsonNode nodeInput;
                try {
                    nodeInput = node.getInputMapping().isEmpty()
                            ? state.getInput()
                            : expressionEvaluator.evaluateBindings(
                                    node.getInputMapping(), contextRoot);
                } catch (RuntimeException e) {
                    throw new NodeFailure(
                            WorkflowErrorCode.EXPRESSION_EVALUATION_FAILED.name(),
                            safeMessage(e), false);
                }
                if ("approval".equals(node.getType())) {
                    nodeExecution = executeApprovalNode(
                            execution, plan, node, token, nodeInput, state,
                            runnerId, fencingToken);
                } else if ("wait".equals(node.getType())) {
                    nodeExecution = executeWaitNode(
                            execution, plan, node, token, nodeInput, state,
                            runnerId, fencingToken);
                } else if ("sub_workflow".equals(node.getType())) {
                    nodeExecution = executeSubWorkflowNode(
                            execution, plan, node, token, nodeInput, state,
                            runnerId, fencingToken);
                } else {
                    nodeExecution = executeNode(
                            execution,
                            node,
                            token,
                            nodeInput,
                            resources,
                            cancellation,
                            runnerId,
                            fencingToken,
                            leaseSeconds,
                            workflowDeadline,
                            state,
                            plan.getContentHash());
                }
                if (nodeExecution == null) {
                    return;
                }
            } catch (NodeFailure failure) {
                if (!"SKIP".equals(node.getOnError()) || !canSkip(failure)) {
                    throw failure;
                }
                String nodeRunId = logicalNodeRunId(
                        execution.getExecutionId(), node.getId(), token.getBranchPath());
                WorkflowNodeRun skippedRun = newNodeRun(
                        execution, node, token, nodeRunId,
                        nextAttemptNo(execution.getExecutionId(), nodeRunId), fencingToken);
                skippedRun.setStatus("SKIPPED");
                skippedRun.setSideEffectStatus("NONE");
                skippedRun.setErrorCode(failure.code);
                skippedRun.setErrorMessage(failure.getMessage());
                skippedRun.setFinishTime(new Date());
                state.getOutputs().put(node.getId(),
                        com.fasterxml.jackson.databind.node.NullNode.instance);
                state.getCompletedKeys().add(instanceKey);
                enqueueOutgoing(node, token, outgoing.getOrDefault(node.getId(), List.of()),
                        execution, state);
                persistence.skipNodeAndCheckpoint(
                        execution.getExecutionId(), runnerId, fencingToken,
                        skippedRun, writeJson(state), plan.getContentHash(), failure.code);
                continue;
            }
            state.getOutputs().put(node.getId(), nodeExecution.result().output() == null
                    ? com.fasterxml.jackson.databind.node.NullNode.instance
                    : nodeExecution.result().output());
            if ("approval".equals(node.getType())) {
                state.setApproval(nodeExecution.result().output());
            }
            NodeFailure budgetFailure = accumulateUsage(plan, state, nodeExecution.result());
            state.getCompletedKeys().add(instanceKey);
            registerCompensation(state, node, token);
            enqueueOutgoing(node, token, outgoing.getOrDefault(node.getId(), List.of()),
                    execution, state);
            persistence.completeNodeAndCheckpoint(
                    execution.getExecutionId(),
                    runnerId,
                    fencingToken,
                    nodeExecution.run(),
                    writeJson(state),
                    writeJson(state.getUsage()),
                    plan.getContentHash());
            if (budgetFailure != null) {
                throw budgetFailure;
            }
        }
        if (!state.isReachedEnd()) {
            throw new NodeFailure(WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                    "执行队列耗尽但未到达结束节点", false);
        }
        JsonNode root = contextRoot(execution, state, "root");
        JsonNode output = plan.getOutputs().isEmpty()
                ? root.path("nodes")
                : expressionEvaluator.evaluateBindings(plan.getOutputs(), root);
        String outputJson = writeJson(output);
        ensureInlineSize(outputJson);
        persistence.finishExecution(
                execution.getExecutionId(), runnerId, fencingToken,
                "SUCCEEDED", persistedJson(output), null, null, "EXECUTION_SUCCEEDED");
    }

    private void registerCompensation(
            WorkflowRuntimeState state,
            WorkflowExecutionPlan.PlanNode node,
            WorkflowRuntimeState.Token token) {
        if (!"WRITE".equals(node.getSideEffect())
                || node.getCompensationNodeId() == null
                || node.getCompensationNodeId().isBlank()) {
            return;
        }
        String key = node.getId() + "@" + token.getBranchPath();
        if (state.getCompensations().stream().noneMatch(item -> key.equals(item.key()))) {
            state.getCompensations().add(new WorkflowRuntimeState.CompensationCandidate(
                    node.getId(), token.getBranchPath(), node.getCompensationNodeId()));
        }
    }

    private boolean runCompensations(
            WorkflowExecution execution,
            WorkflowExecutionPlan plan,
            WorkflowRuntimeState state,
            Map<String, ResolvedWorkflowResource> resources,
            String runnerId,
            long fencingToken,
            int leaseSeconds,
            NodeFailure originalFailure) throws Exception {
        Map<String, WorkflowExecutionPlan.PlanNode> nodes = new HashMap<>();
        plan.getNodes().forEach(node -> nodes.put(node.getId(), node));
        WorkflowCancellation compensationCancellation = () -> !leaseValid(
                execution.getExecutionId(), runnerId, fencingToken);
        for (int index = state.getCompensations().size() - 1; index >= 0; index--) {
            WorkflowRuntimeState.CompensationCandidate candidate =
                    state.getCompensations().get(index);
            if (state.getCompensatedKeys().contains(candidate.key())) {
                continue;
            }
            WorkflowExecutionPlan.PlanNode compensation = nodes.get(
                    candidate.getCompensationNodeId());
            if (compensation == null) {
                return false;
            }
            WorkflowNodeHandler handler = nodeRegistry.findHandler(
                            compensation.getType(), compensation.getHandlerVersion())
                    .orElse(null);
            if (handler == null || !"WRITE".equals(compensation.getSideEffect())) {
                return false;
            }
            WorkflowRuntimeState.Token token = new WorkflowRuntimeState.Token(
                    compensation.getId(), "compensation:" + candidate.key());
            String nodeRunId = logicalNodeRunId(
                    execution.getExecutionId(), compensation.getId(), token.getBranchPath());
            WorkflowNodeRun run = newNodeRun(
                    execution, compensation, token, nodeRunId,
                    nextAttemptNo(execution.getExecutionId(), nodeRunId), fencingToken);
            ObjectNode input = objectMapper.createObjectNode();
            input.put("originalNodeId", candidate.getNodeId());
            input.put("originalBranchPath", candidate.getBranchPath());
            input.set("originalOutput", state.getOutputs().getOrDefault(
                    candidate.getNodeId(),
                    com.fasterxml.jackson.databind.node.NullNode.instance));
            input.set("executionInput", state.getInput());
            ObjectNode failure = input.putObject("failure");
            failure.put("code", originalFailure.code);
            failure.put("message", originalFailure.getMessage());
            String inputJson = writeJson(input);
            ensureInlineSize(inputJson);
            run.setInputJson(persistedJson(input));
            persistence.startNode(
                    execution.getExecutionId(), runnerId, fencingToken, run);
            persistence.appendEvent(
                    execution.getExecutionId(), runnerId, fencingToken,
                    "COMPENSATION_STARTED", nodeRunId, compensation.getId(),
                    Map.of("originalNodeId", candidate.getNodeId()));
            WorkflowNodeContext context = new WorkflowNodeContext(
                    execution.getExecutionId(), nodeRunId, run.getAttemptNo(),
                    execution.getTenantId(), execution.getPrincipalType(), execution.getPrincipalId(),
                    input, compensation.getConfig(),
                    resourceResolver.forNode(compensation, resources), compensationCancellation);
            try {
                long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(
                        Math.min(compensation.getTimeoutSeconds(), 900));
                WorkflowNodeResult result = executeWithHeartbeat(
                        handler, context, compensation.getTimeoutSeconds(),
                        execution.getExecutionId(), runnerId, fencingToken, leaseSeconds,
                        deadline, compensationCancellation);
                validateFrozenOutput(compensation, result);
                String outputJson = writeJson(result.output());
                ensureInlineSize(outputJson);
                run.setStatus("SUCCEEDED");
                run.setSideEffectStatus(result.sideEffectStatus());
                run.setOutputJson(persistedJson(result.output()));
                run.setFinishTime(new Date());
                state.getCompensatedKeys().add(candidate.key());
                persistence.completeNodeAndCheckpoint(
                        execution.getExecutionId(), runnerId, fencingToken, run,
                        writeJson(state), writeJson(state.getUsage()), plan.getContentHash());
                persistence.appendEvent(
                        execution.getExecutionId(), runnerId, fencingToken,
                        "COMPENSATION_SUCCEEDED", nodeRunId, compensation.getId(),
                        Map.of("originalNodeId", candidate.getNodeId()));
            } catch (LeaseLostException e) {
                throw e;
            } catch (Exception e) {
                run.setStatus("FAILED");
                run.setErrorCode(errorCode(e));
                run.setErrorMessage(safeMessage(e));
                run.setSideEffectStatus("UNCONFIRMED");
                run.setFinishTime(new Date());
                persistence.failNode(
                        execution.getExecutionId(), runnerId, fencingToken, run);
                persistence.appendEvent(
                        execution.getExecutionId(), runnerId, fencingToken,
                        "COMPENSATION_FAILED", nodeRunId, compensation.getId(),
                        Map.of("originalNodeId", candidate.getNodeId(),
                                "errorCode", errorCode(e)));
                return false;
            }
        }
        return true;
    }

    private boolean leaseValid(
            String executionId, String runnerId, long fencingToken) {
        WorkflowExecution current = executionMapper.selectByExecutionId(executionId);
        return current != null
                && runnerId.equals(current.getRunnerId())
                && current.getFencingToken() != null
                && current.getFencingToken() == fencingToken
                && Set.of("RUNNING", "RECOVERING").contains(current.getStatus());
    }

    /**
     * 选取一批顺序稳定且相互独立的数据节点并行执行。
     * 控制节点和写节点会改变调度状态或要求副作用有序处理，因此仍保持串行执行。
     */
    private List<WorkflowRuntimeState.Token> takeParallelBatch(
            WorkflowRuntimeState state,
            Map<String, WorkflowExecutionPlan.PlanNode> nodes,
            int maximum) {
        if (maximum < 2 || state.getPending().size() < 2) {
            return List.of();
        }
        List<Integer> indexes = new ArrayList<>();
        Set<String> instances = new HashSet<>();
        for (int index = 0; index < state.getPending().size()
                && indexes.size() < maximum; index++) {
            WorkflowRuntimeState.Token token = state.getPending().get(index);
            WorkflowExecutionPlan.PlanNode node = nodes.get(token.getNodeId());
            String instanceKey = token.getNodeId() + "@" + token.getBranchPath();
            if (node == null || state.getCompletedKeys().contains(instanceKey)
                    || !instances.add(instanceKey) || !parallelizable(node)) {
                continue;
            }
            indexes.add(index);
        }
        if (indexes.size() < 2) {
            return List.of();
        }
        List<WorkflowRuntimeState.Token> result = new ArrayList<>();
        indexes.forEach(index -> result.add(state.getPending().get(index)));
        for (int index = indexes.size() - 1; index >= 0; index--) {
            state.getPending().remove((int) indexes.get(index));
        }
        return result;
    }

    private boolean parallelizable(WorkflowExecutionPlan.PlanNode node) {
        return !"WRITE".equals(node.getSideEffect())
                && !Set.of("approval", "wait", "condition", "parallel", "join", "loop")
                .contains(node.getType());
    }

    private boolean executeParallelBatch(
            WorkflowExecution execution,
            WorkflowExecutionPlan plan,
            WorkflowRuntimeState state,
            List<WorkflowRuntimeState.Token> tokens,
            Map<String, WorkflowExecutionPlan.PlanNode> nodes,
            Map<String, List<WorkflowExecutionPlan.PlanEdge>> outgoing,
            Map<String, ResolvedWorkflowResource> resources,
            WorkflowCancellation cancellation,
            String runnerId,
            long fencingToken,
            int leaseSeconds,
            long workflowDeadline,
            int maxNodeRuns) throws Exception {
        List<ParallelAttempt> attempts = new ArrayList<>();
        try {
            for (WorkflowRuntimeState.Token token : tokens) {
                state.setTotalNodeRuns(state.getTotalNodeRuns() + 1);
                if (state.getTotalNodeRuns() > maxNodeRuns) {
                    throw new NodeFailure(WorkflowErrorCode.QUOTA_EXCEEDED.name(),
                            "节点运行次数超过工作流上限", false);
                }
                WorkflowExecutionPlan.PlanNode node = nodes.get(token.getNodeId());
                JsonNode contextRoot = contextRoot(execution, state, token.getBranchPath());
                JsonNode input;
                try {
                    input = node.getInputMapping().isEmpty()
                            ? state.getInput()
                            : expressionEvaluator.evaluateBindings(node.getInputMapping(), contextRoot);
                } catch (RuntimeException e) {
                    throw new NodeFailure(
                            WorkflowErrorCode.EXPRESSION_EVALUATION_FAILED.name(),
                            safeMessage(e), false);
                }
                attempts.add(startParallelAttempt(
                        execution, node, token, input, resources, cancellation,
                        runnerId, fencingToken, workflowDeadline));
            }

            List<ParallelOutcome> outcomes = new ArrayList<>();
            for (ParallelAttempt attempt : attempts) {
                outcomes.add(awaitParallelAttempt(
                        attempt, execution.getExecutionId(), runnerId, fencingToken,
                        leaseSeconds, workflowDeadline, cancellation));
            }

            NodeFailure terminalFailure = null;
            NodeFailure budgetFailure = null;
            List<ParallelOutcome> retries = new ArrayList<>();
            long retryDelayMs = 0;
            for (ParallelOutcome outcome : outcomes) {
                WorkflowExecutionPlan.PlanNode node = outcome.attempt().node();
                WorkflowRuntimeState.Token token = outcome.attempt().token();
                WorkflowNodeRun run = outcome.attempt().run();
                String instanceKey = node.getId() + "@" + token.getBranchPath();
                if (outcome.error() != null) {
                    String code = cancellation.isCancellationRequested()
                            ? WorkflowErrorCode.NODE_CANCELLED.name()
                            : errorCode(outcome.error());
                    run.setStatus(WorkflowErrorCode.NODE_CANCELLED.name().equals(code)
                            ? "CANCELLED" : "FAILED");
                    run.setErrorCode(code);
                    run.setErrorMessage(safeMessage(outcome.error()));
                    run.setSideEffectStatus("NONE");
                    run.setFinishTime(new Date());
                    persistence.failNode(execution.getExecutionId(), runnerId, fencingToken, run);
                    if (WorkflowErrorCode.NODE_CANCELLED.name().equals(code)) {
                        terminalFailure = terminalFailure == null
                                ? new NodeFailure(code, "工作流执行已取消", false)
                                : terminalFailure;
                    } else if (run.getAttemptNo() < outcome.attempt().maxAttempts()
                            && isRetryable(node, code)) {
                        retries.add(outcome);
                        retryDelayMs = Math.max(retryDelayMs,
                                retryDelay(node, run.getAttemptNo()));
                    } else if ("SKIP".equals(node.getOnError())
                            && canSkip(new NodeFailure(code, run.getErrorMessage(), false))) {
                        WorkflowNodeRun skippedRun = newNodeRun(
                                execution, node, token, run.getNodeRunId(),
                                nextAttemptNo(execution.getExecutionId(), run.getNodeRunId()),
                                fencingToken);
                        skippedRun.setStatus("SKIPPED");
                        skippedRun.setSideEffectStatus("NONE");
                        skippedRun.setErrorCode(code);
                        skippedRun.setErrorMessage(run.getErrorMessage());
                        skippedRun.setFinishTime(new Date());
                        state.getOutputs().put(node.getId(),
                                com.fasterxml.jackson.databind.node.NullNode.instance);
                        state.getCompletedKeys().add(instanceKey);
                        enqueueOutgoing(node, token,
                                outgoing.getOrDefault(node.getId(), List.of()), execution, state);
                        persistence.skipNodeAndCheckpoint(
                                execution.getExecutionId(), runnerId, fencingToken,
                                skippedRun, writeJson(state), plan.getContentHash(), code);
                    } else if (terminalFailure == null) {
                        terminalFailure = new NodeFailure(code, run.getErrorMessage(), false);
                    }
                    continue;
                }

                WorkflowNodeResult result = outcome.result();
                run.setStatus("SUCCEEDED");
                run.setSideEffectStatus(result.sideEffectStatus());
                run.setOutputJson(persistedJson(result.output()));
                run.setFinishTime(new Date());
                state.getOutputs().put(node.getId(), result.output() == null
                        ? com.fasterxml.jackson.databind.node.NullNode.instance
                        : result.output());
                NodeFailure currentBudgetFailure = accumulateUsage(plan, state, result);
                if (budgetFailure == null) {
                    budgetFailure = currentBudgetFailure;
                }
                state.getCompletedKeys().add(instanceKey);
                registerCompensation(state, node, token);
                enqueueOutgoing(node, token,
                        outgoing.getOrDefault(node.getId(), List.of()), execution, state);
                persistence.completeNodeAndCheckpoint(
                        execution.getExecutionId(), runnerId, fencingToken, run,
                        writeJson(state), writeJson(state.getUsage()), plan.getContentHash());
            }
            if (terminalFailure != null) {
                throw terminalFailure;
            }
            if (budgetFailure != null) {
                throw budgetFailure;
            }
            if (!retries.isEmpty()) {
                List<WorkflowRuntimeState.Token> retryTokens = retries.stream()
                        .map(item -> item.attempt().token()).toList();
                state.getPending().addAll(0, retryTokens);
                ParallelOutcome first = retries.get(0);
                Date resumeTime = new Date(System.currentTimeMillis() + retryDelayMs);
                persistence.suspendForRetry(
                        execution.getExecutionId(), runnerId, fencingToken,
                        first.attempt().run().getNodeRunId(), first.attempt().node().getId(),
                        first.attempt().run().getAttemptNo() + 1, retryDelayMs, resumeTime,
                        writeJson(state), plan.getContentHash());
                return true;
            }
            return false;
        } catch (Exception e) {
            attempts.forEach(attempt -> attempt.future().cancel(true));
            cleanupParallelAttempts(
                    execution.getExecutionId(), runnerId, fencingToken, attempts, e);
            throw e;
        }
    }

    private void cleanupParallelAttempts(
            String executionId,
            String runnerId,
            long fencingToken,
            List<ParallelAttempt> attempts,
            Exception failure) {
        if (!leaseValid(executionId, runnerId, fencingToken)) {
            return;
        }
        for (ParallelAttempt attempt : attempts) {
            WorkflowNodeRun run = nodeRunMapper.selectById(attempt.run().getId());
            if (run == null || !"RUNNING".equals(run.getStatus())) {
                continue;
            }
            try {
                run.setStatus("FAILED");
                run.setErrorCode(errorCode(failure));
                run.setErrorMessage(safeMessage(failure));
                run.setSideEffectStatus("NONE");
                run.setFinishTime(new Date());
                persistence.failNode(executionId, runnerId, fencingToken, run);
            } catch (Exception cleanupError) {
                log.warn("Unable to close parallel node run {}: {}",
                        run.getNodeRunId(), cleanupError.getMessage());
            }
        }
    }

    private ParallelAttempt startParallelAttempt(
            WorkflowExecution execution,
            WorkflowExecutionPlan.PlanNode node,
            WorkflowRuntimeState.Token token,
            JsonNode input,
            Map<String, ResolvedWorkflowResource> resources,
            WorkflowCancellation cancellation,
            String runnerId,
            long fencingToken,
            long workflowDeadline) {
        WorkflowNodeHandler handler = nodeRegistry.findHandler(node.getType(), node.getHandlerVersion())
                .orElseThrow(() -> new NodeFailure(WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                        "节点处理器版本不可用: " + node.getType() + ":" + node.getHandlerVersion(), false));
        String nodeRunId = logicalNodeRunId(
                execution.getExecutionId(), node.getId(), token.getBranchPath());
        int maxAttempts = node.getRetryPolicy() == null
                || node.getRetryPolicy().getMaxAttempts() == null
                ? 1 : node.getRetryPolicy().getMaxAttempts();
        int attemptNo = prepareAttemptBase(execution.getExecutionId(), nodeRunId) + 1;
        if (attemptNo > maxAttempts) {
            throw new NodeFailure(WorkflowErrorCode.EXECUTION_CONFLICT.name(),
                    "节点尝试次数已超过发布策略", false);
        }
        WorkflowNodeRun run = newNodeRun(
                execution, node, token, nodeRunId, attemptNo, fencingToken);
        String inputJson = writeJson(input);
        ensureInlineSize(inputJson);
        run.setInputJson(persistedJson(input));
        persistence.startNode(execution.getExecutionId(), runnerId, fencingToken, run);
        WorkflowNodeContext context = new WorkflowNodeContext(
                execution.getExecutionId(), nodeRunId, attemptNo,
                execution.getTenantId(), execution.getPrincipalType(), execution.getPrincipalId(),
                input, node.getConfig(), resourceResolver.forNode(node, resources), cancellation);
        Future<WorkflowNodeResult> future = nodeExecutor.submit(() -> handler.execute(context));
        long nodeDeadline = Math.min(workflowDeadline,
                System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(node.getTimeoutSeconds()));
        return new ParallelAttempt(node, token, run, maxAttempts, nodeDeadline, future);
    }

    private ParallelOutcome awaitParallelAttempt(
            ParallelAttempt attempt,
            String executionId,
            String runnerId,
            long fencingToken,
            int leaseSeconds,
            long workflowDeadline,
            WorkflowCancellation cancellation) {
        long heartbeatMs = TimeUnit.SECONDS.toMillis(Math.max(1, leaseSeconds / 3));
        while (true) {
            long remainingMs = Math.min(
                    attempt.deadlineMillis() - System.currentTimeMillis(),
                    workflowDeadline - System.currentTimeMillis());
            if (remainingMs <= 0) {
                attempt.future().cancel(true);
                String code = System.currentTimeMillis() >= workflowDeadline
                        ? WorkflowErrorCode.EXECUTION_TIMEOUT.name()
                        : WorkflowErrorCode.NODE_TIMEOUT.name();
                return new ParallelOutcome(attempt, null,
                        new NodeFailure(code, "节点执行超时", false));
            }
            try {
                WorkflowNodeResult result = attempt.future().get(
                        Math.min(remainingMs, heartbeatMs), TimeUnit.MILLISECONDS);
                validateFrozenOutput(attempt.node(), result);
                String outputJson = writeJson(result.output());
                ensureInlineSize(outputJson);
                return new ParallelOutcome(attempt, result, null);
            } catch (TimeoutException e) {
                ensureLease(executionId, runnerId, fencingToken, leaseSeconds);
                if (cancellation.isCancellationRequested()) {
                    attempt.future().cancel(true);
                    return new ParallelOutcome(attempt, null,
                            new NodeFailure(WorkflowErrorCode.NODE_CANCELLED.name(),
                                    "工作流执行已取消", false));
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                return new ParallelOutcome(attempt, null,
                        cause instanceof Exception exception
                                ? exception : new IllegalStateException(cause));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ParallelOutcome(attempt, null,
                        new NodeFailure(WorkflowErrorCode.NODE_CANCELLED.name(),
                                "工作流执行线程已中断", false));
            }
        }
    }

    private NodeExecution executeApprovalNode(
            WorkflowExecution execution,
            WorkflowExecutionPlan plan,
            WorkflowExecutionPlan.PlanNode node,
            WorkflowRuntimeState.Token token,
            JsonNode input,
            WorkflowRuntimeState state,
            String runnerId,
            long fencingToken) {
        String nodeRunId = logicalNodeRunId(
                execution.getExecutionId(), node.getId(), token.getBranchPath());
        WorkflowApprovalTask existing = approvalTaskMapper.selectByNodeRun(
                execution.getExecutionId(), nodeRunId);
        if (existing != null) {
            if (!"APPROVED".equals(existing.getStatus())) {
                throw new NodeFailure(WorkflowErrorCode.EXECUTION_CONFLICT.name(),
                        "审批节点尚未完成或已被终止", false);
            }
            WorkflowNodeRun run = waitingNodeRun(execution.getExecutionId(), nodeRunId);
            ObjectNode output = objectMapper.createObjectNode();
            output.put("status", "APPROVED");
            output.put("approvalTaskId", existing.getApprovalTaskId());
            output.put("finishedAt", existing.getFinishTime() == null
                    ? System.currentTimeMillis() : existing.getFinishTime().getTime());
            WorkflowNodeResult result = WorkflowNodeResult.success(output);
            validateFrozenOutput(node, result);
            run.setStatus("SUCCEEDED");
            run.setOutputJson(persistedJson(output));
            run.setFinishTime(new Date());
            return new NodeExecution(result, run);
        }
        WorkflowNodeRun run = newNodeRun(
                execution, node, token, nodeRunId,
                nextAttemptNo(execution.getExecutionId(), nodeRunId), fencingToken);
        run.setStatus("WAITING");
        run.setInputJson(persistedJson(input));
        WorkflowApprovalTask task = new WorkflowApprovalTask();
        task.setTenantId(execution.getTenantId());
        task.setApprovalTaskId(UUID.randomUUID().toString());
        task.setExecutionId(execution.getExecutionId());
        task.setNodeRunId(nodeRunId);
        task.setAssigneeType(node.getConfig().path("assigneeType").asText());
        task.setAssigneeSnapshot(writeJson(node.getConfig()));
        task.setApprovalMode(node.getConfig().path("approvalMode").asText("ANY"));
        task.setRequiredApprovals(node.getConfig().path("requiredApprovals").asInt(1));
        task.setAllowSelfApproval(node.getConfig().path("allowSelfApproval").asBoolean(false));
        task.setStatus("PENDING");
        task.setDecisionSummary("{\"decisions\":[]}");
        int timeoutSeconds = node.getConfig().path("timeoutSeconds").asInt(86400);
        task.setDeadline(new Date(System.currentTimeMillis()
                + TimeUnit.SECONDS.toMillis(timeoutSeconds)));
        task.setLockVersion(0);
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());
        state.setTotalNodeRuns(Math.max(0, state.getTotalNodeRuns() - 1));
        state.getPending().add(0, token);
        persistence.suspendForApproval(
                execution.getExecutionId(), runnerId, fencingToken, run, task,
                writeJson(state), plan.getContentHash());
        return null;
    }

    private NodeExecution executeWaitNode(
            WorkflowExecution execution,
            WorkflowExecutionPlan plan,
            WorkflowExecutionPlan.PlanNode node,
            WorkflowRuntimeState.Token token,
            JsonNode input,
            WorkflowRuntimeState state,
            String runnerId,
            long fencingToken) {
        String nodeRunId = logicalNodeRunId(
                execution.getExecutionId(), node.getId(), token.getBranchPath());
        List<WorkflowNodeRun> existing = nodeRunMapper.selectList(
                new LambdaQueryWrapper<WorkflowNodeRun>()
                        .eq(WorkflowNodeRun::getExecutionId, execution.getExecutionId())
                        .eq(WorkflowNodeRun::getNodeRunId, nodeRunId)
                        .orderByDesc(WorkflowNodeRun::getAttemptNo)
                        .last("LIMIT 1"));
        if (!existing.isEmpty()) {
            WorkflowNodeRun run = existing.get(0);
            if (!"WAITING".equals(run.getStatus())) {
                throw new NodeFailure(WorkflowErrorCode.EXECUTION_CONFLICT.name(),
                        "等待节点状态无法恢复", false);
            }
            ObjectNode output = objectMapper.createObjectNode();
            output.put("status", "RESUMED");
            output.put("resumedAt", System.currentTimeMillis());
            WorkflowNodeResult result = WorkflowNodeResult.success(output);
            validateFrozenOutput(node, result);
            run.setStatus("SUCCEEDED");
            run.setOutputJson(persistedJson(output));
            run.setFinishTime(new Date());
            return new NodeExecution(result, run);
        }
        WorkflowNodeRun run = newNodeRun(
                execution, node, token, nodeRunId,
                nextAttemptNo(execution.getExecutionId(), nodeRunId), fencingToken);
        run.setStatus("WAITING");
        run.setInputJson(persistedJson(input));
        int delaySeconds = node.getConfig().path("delaySeconds").asInt();
        Date resumeTime = new Date(System.currentTimeMillis()
                + TimeUnit.SECONDS.toMillis(delaySeconds));
        state.setTotalNodeRuns(Math.max(0, state.getTotalNodeRuns() - 1));
        state.getPending().add(0, token);
        persistence.suspendForWait(
                execution.getExecutionId(), runnerId, fencingToken, run, resumeTime,
                writeJson(state), plan.getContentHash());
        return null;
    }

    private NodeExecution executeSubWorkflowNode(
            WorkflowExecution execution,
            WorkflowExecutionPlan plan,
            WorkflowExecutionPlan.PlanNode node,
            WorkflowRuntimeState.Token token,
            JsonNode input,
            WorkflowRuntimeState state,
            String runnerId,
            long fencingToken) {
        String nodeRunId = logicalNodeRunId(
                execution.getExecutionId(), node.getId(), token.getBranchPath());
        List<WorkflowNodeRun> existing = nodeRunMapper.selectList(
                new LambdaQueryWrapper<WorkflowNodeRun>()
                        .eq(WorkflowNodeRun::getExecutionId, execution.getExecutionId())
                        .eq(WorkflowNodeRun::getNodeRunId, nodeRunId)
                        .orderByDesc(WorkflowNodeRun::getAttemptNo)
                        .last("LIMIT 1"));
        WorkflowNodeRun run;
        WorkflowExecution child;
        boolean created = existing.isEmpty();
        if (created) {
            child = executionService.startChild(
                    execution,
                    node.getConfig().path("workflowCode").asText(),
                    node.getConfig().path("workflowVersionId").asText(),
                    input,
                    nodeRunId);
            run = newNodeRun(
                    execution, node, token, nodeRunId,
                    nextAttemptNo(execution.getExecutionId(), nodeRunId), fencingToken);
            run.setStatus("WAITING");
            run.setInputJson(persistedJson(input));
            ObjectNode waitingOutput = objectMapper.createObjectNode();
            waitingOutput.put("childExecutionId", child.getExecutionId());
            waitingOutput.put("status", child.getStatus());
            run.setOutputJson(persistedJson(waitingOutput));
        } else {
            run = existing.get(0);
            if (!"WAITING".equals(run.getStatus())) {
                throw new NodeFailure(WorkflowErrorCode.EXECUTION_CONFLICT.name(),
                        "子工作流节点状态无法恢复", false);
            }
            String childExecutionId;
            try {
                childExecutionId = objectMapper.readTree(run.getOutputJson())
                        .path("childExecutionId").asText();
            } catch (Exception e) {
                throw new NodeFailure(WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                        "子工作流节点状态已损坏", false);
            }
            child = executionMapper.selectByExecutionId(childExecutionId);
            if (child == null) {
                throw new NodeFailure(WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                        "子工作流执行不存在", false);
            }
        }
        if ("SUCCEEDED".equals(child.getStatus())) {
            ObjectNode output = objectMapper.createObjectNode();
            output.put("childExecutionId", child.getExecutionId());
            output.put("status", child.getStatus());
            try {
                output.set("output", child.getOutputJson() == null
                        ? com.fasterxml.jackson.databind.node.NullNode.instance
                        : objectMapper.readTree(child.getOutputJson()));
            } catch (Exception e) {
                throw new NodeFailure(WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                        "子工作流输出格式无效", false);
            }
            WorkflowNodeResult result = WorkflowNodeResult.success(output);
            validateFrozenOutput(node, result);
            run.setStatus("SUCCEEDED");
            run.setOutputJson(persistedJson(output));
            run.setFinishTime(new Date());
            return new NodeExecution(result, run);
        }
        if (Set.of("FAILED", "CANCELLED", "REJECTED", "NEEDS_ATTENTION")
                .contains(child.getStatus())) {
            run.setStatus("FAILED");
            run.setErrorCode(child.getErrorCode() == null
                    ? WorkflowErrorCode.INTERNAL_ERROR.name() : child.getErrorCode());
            run.setErrorMessage("子工作流执行失败: "
                    + (child.getErrorMessage() == null ? child.getStatus() : child.getErrorMessage()));
            run.setFinishTime(new Date());
            persistence.failNode(
                    execution.getExecutionId(), runnerId, fencingToken, run);
            throw new NodeFailure(run.getErrorCode(), run.getErrorMessage(),
                    "NEEDS_ATTENTION".equals(child.getStatus()));
        }
        int pollSeconds = node.getConfig().path("pollSeconds").asInt(2);
        Date resumeTime = new Date(System.currentTimeMillis()
                + TimeUnit.SECONDS.toMillis(Math.max(1, Math.min(pollSeconds, 60))));
        state.setTotalNodeRuns(Math.max(0, state.getTotalNodeRuns() - 1));
        state.getPending().add(0, token);
        persistence.suspendForChild(
                execution.getExecutionId(), runnerId, fencingToken, run, created,
                child.getExecutionId(), resumeTime, writeJson(state), plan.getContentHash());
        return null;
    }

    private WorkflowNodeRun waitingNodeRun(String executionId, String nodeRunId) {
        return nodeRunMapper.selectList(new LambdaQueryWrapper<WorkflowNodeRun>()
                        .eq(WorkflowNodeRun::getExecutionId, executionId)
                        .eq(WorkflowNodeRun::getNodeRunId, nodeRunId)
                        .eq(WorkflowNodeRun::getStatus, "WAITING")
                        .orderByDesc(WorkflowNodeRun::getAttemptNo)
                        .last("LIMIT 1"))
                .stream().findFirst()
                .orElseThrow(() -> new NodeFailure(
                        WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                        "审批节点运行记录不存在", false));
    }

    private NodeExecution executeNode(
            WorkflowExecution execution,
            WorkflowExecutionPlan.PlanNode node,
            WorkflowRuntimeState.Token token,
            JsonNode input,
            Map<String, ResolvedWorkflowResource> resources,
            WorkflowCancellation cancellation,
            String runnerId,
            long fencingToken,
            int leaseSeconds,
            long workflowDeadline,
            WorkflowRuntimeState state,
            String planHash) throws Exception {
        WorkflowNodeHandler handler = nodeRegistry.findHandler(node.getType(), node.getHandlerVersion())
                .orElseThrow(() -> new NodeFailure(WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                        "节点处理器版本不可用: " + node.getType() + ":" + node.getHandlerVersion(), false));
        String nodeRunId = logicalNodeRunId(
                execution.getExecutionId(), node.getId(), token.getBranchPath());
        int maxAttempts = node.getRetryPolicy() == null
                || node.getRetryPolicy().getMaxAttempts() == null
                ? 1 : node.getRetryPolicy().getMaxAttempts();
        int attemptBase = prepareAttemptBase(execution.getExecutionId(), nodeRunId);
        Exception lastError = null;
        for (int attempt = attemptBase + 1; attempt <= maxAttempts; attempt++) {
            WorkflowNodeRun run = newNodeRun(
                    execution, node, token, nodeRunId, attempt, fencingToken);
            String inputJson = writeJson(input);
            ensureInlineSize(inputJson);
            run.setInputJson(persistedJson(input));
            persistence.startNode(execution.getExecutionId(), runnerId, fencingToken, run);
            WorkflowNodeContext context = new WorkflowNodeContext(
                    execution.getExecutionId(),
                    nodeRunId,
                    attempt,
                    execution.getTenantId(),
                    execution.getPrincipalType(),
                    execution.getPrincipalId(),
                    input,
                    node.getConfig(),
                    resourceResolver.forNode(node, resources),
                    cancellation);
            try {
                WorkflowNodeResult result = executeWithHeartbeat(
                        handler, context, node.getTimeoutSeconds(), execution.getExecutionId(),
                        runnerId, fencingToken, leaseSeconds, workflowDeadline, cancellation);
                validateFrozenOutput(node, result);
                String outputJson = writeJson(result.output());
                ensureInlineSize(outputJson);
                run.setStatus("SUCCEEDED");
                run.setSideEffectStatus(result.sideEffectStatus());
                run.setOutputJson(persistedJson(result.output()));
                run.setFinishTime(new Date());
                return new NodeExecution(result, run);
            } catch (LeaseLostException e) {
                throw e;
            } catch (Exception e) {
                lastError = e;
                boolean cancelled = cancellation.isCancellationRequested();
                run.setStatus(cancelled ? "CANCELLED" : "FAILED");
                run.setErrorCode(cancelled
                        ? WorkflowErrorCode.NODE_CANCELLED.name() : errorCode(e));
                run.setErrorMessage(safeMessage(e));
                run.setSideEffectStatus("WRITE".equals(node.getSideEffect())
                        ? "UNCONFIRMED" : "NONE");
                run.setFinishTime(new Date());
                persistence.failNode(execution.getExecutionId(), runnerId, fencingToken, run);
                if (cancelled) {
                    throw new NodeFailure(WorkflowErrorCode.NODE_CANCELLED.name(),
                            "工作流执行已取消", false);
                }
                if ("WRITE".equals(node.getSideEffect())) {
                    throw new NodeFailure(WorkflowErrorCode.SIDE_EFFECT_UNCONFIRMED.name(),
                            "写节点执行结果不确定，需要人工确认", true);
                }
                if (attempt < maxAttempts && isRetryable(node, errorCode(e))) {
                    long delayMs = retryDelay(node, attempt);
                    state.getPending().add(0, token);
                    persistence.suspendForRetry(
                            execution.getExecutionId(), runnerId, fencingToken,
                            nodeRunId, node.getId(), attempt + 1, delayMs,
                            new Date(System.currentTimeMillis() + delayMs),
                            writeJson(state), planHash);
                    return null;
                }
                break;
            }
        }
        throw new NodeFailure(errorCode(lastError), safeMessage(lastError), false);
    }

    private WorkflowNodeResult executeWithHeartbeat(
            WorkflowNodeHandler handler,
            WorkflowNodeContext context,
            int timeoutSeconds,
            String executionId,
            String runnerId,
            long fencingToken,
            int leaseSeconds,
            long workflowDeadline,
            WorkflowCancellation cancellation) throws Exception {
        Future<WorkflowNodeResult> future = nodeExecutor.submit(() -> handler.execute(context));
        long nodeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        long heartbeatSeconds = Math.max(1, leaseSeconds / 3);
        while (true) {
            long workflowRemaining = TimeUnit.MILLISECONDS.toNanos(
                    Math.max(0, workflowDeadline - System.currentTimeMillis()));
            long remainingNanos = Math.min(
                    nodeDeadline - System.nanoTime(), workflowRemaining);
            if (remainingNanos <= 0) {
                future.cancel(true);
                if (System.currentTimeMillis() >= workflowDeadline) {
                    throw new NodeFailure(WorkflowErrorCode.EXECUTION_TIMEOUT.name(),
                            "工作流总执行时间超过上限", false);
                }
                throw new NodeFailure(WorkflowErrorCode.NODE_TIMEOUT.name(),
                        "节点执行超时", false);
            }
            try {
                long waitNanos = Math.min(remainingNanos,
                        TimeUnit.SECONDS.toNanos(heartbeatSeconds));
                return future.get(waitNanos, TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                ensureLease(executionId, runnerId, fencingToken, leaseSeconds);
                if (cancellation.isCancellationRequested()) {
                    future.cancel(true);
                    throw new NodeFailure(WorkflowErrorCode.NODE_CANCELLED.name(),
                            "工作流执行已取消", false);
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception exception) throw exception;
                throw new IllegalStateException(cause);
            }
        }
    }

    private void enqueueOutgoing(
            WorkflowExecutionPlan.PlanNode node,
            WorkflowRuntimeState.Token token,
            List<WorkflowExecutionPlan.PlanEdge> edges,
            WorkflowExecution execution,
            WorkflowRuntimeState state) {
        List<WorkflowExecutionPlan.PlanEdge> selected = new ArrayList<>();
        if ("condition".equals(node.getType())) {
            JsonNode context = contextRoot(execution, state, token.getBranchPath());
            WorkflowExecutionPlan.PlanEdge fallback = null;
            for (WorkflowExecutionPlan.PlanEdge edge : edges.stream()
                    .sorted(Comparator.comparing(edge -> edge.getPriority() == null
                            ? Integer.MAX_VALUE : edge.getPriority())).toList()) {
                if (Boolean.TRUE.equals(edge.getDefaultEdge())) {
                    fallback = edge;
                } else {
                    try {
                        if (expressionEvaluator.evaluateCondition(edge.getExpressionAst(), context)) {
                            selected.add(edge);
                            break;
                        }
                    } catch (RuntimeException e) {
                        if (!"FALSE".equals(edge.getConditionOnError())) {
                            throw new NodeFailure(
                                    WorkflowErrorCode.EXPRESSION_EVALUATION_FAILED.name(),
                                    safeMessage(e), false);
                        }
                    }
                }
            }
            if (selected.isEmpty() && fallback != null) selected.add(fallback);
        } else if ("parallel".equals(node.getType())) {
            selected.addAll(edges);
        } else if ("llm_classifier".equals(node.getType())) {
            String branch = state.getOutputs().getOrDefault(
                    node.getId(), com.fasterxml.jackson.databind.node.NullNode.instance)
                    .path("branch").asText();
            edges.stream().filter(edge -> branch.equals(edge.getSourcePort()))
                    .findFirst().ifPresent(selected::add);
            if (selected.isEmpty()) {
                throw new NodeFailure(WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                        "语义分类结果没有匹配的分支出口", false);
            }
        } else if ("loop".equals(node.getType())) {
            int iteration = state.getLoopCounts().getOrDefault(node.getId(), 0);
            int maximum = node.getConfig().path("maxIterations").asInt();
            WorkflowExecutionPlan.PlanEdge edge = iteration < maximum
                    ? edges.stream().filter(item -> "LOOP".equals(item.getKind())).findFirst().orElse(null)
                    : edges.stream().filter(item -> Boolean.TRUE.equals(item.getDefaultEdge())).findFirst().orElse(null);
            if (edge != null) {
                selected.add(edge);
                if ("LOOP".equals(edge.getKind())) {
                    state.getLoopCounts().put(node.getId(), iteration + 1);
                }
            }
        } else {
            selected.addAll(edges);
        }
        for (WorkflowExecutionPlan.PlanEdge edge : selected) {
            String path = "LOOP".equals(edge.getKind())
                    ? node.getId() + "#" + state.getLoopCounts().get(node.getId())
                    : token.getBranchPath();
            enqueue(state, edge.getTarget(), path);
        }
    }

    private void enqueue(WorkflowRuntimeState state, String nodeId, String branchPath) {
        state.getPending().add(new WorkflowRuntimeState.Token(nodeId, branchPath));
        String key = nodeId + "@" + branchPath;
        state.getArrivals().merge(key, 1, Integer::sum);
    }

    private int requiredJoinArrivals(
            WorkflowExecutionPlan.PlanNode node, int incomingCount) {
        String mode = node.getConfig().path("mode").asText("ALL");
        if ("ANY".equals(mode)) {
            return 1;
        }
        if ("N_OF_M".equals(mode)) {
            return Math.max(1, Math.min(
                    node.getConfig().path("requiredBranches").asInt(1), incomingCount));
        }
        return incomingCount;
    }

    private NodeFailure accumulateUsage(
            WorkflowExecutionPlan plan,
            WorkflowRuntimeState state,
            WorkflowNodeResult result) {
        if (result == null || result.usage() == null || result.usage().isEmpty()) {
            return null;
        }
        result.usage().forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) return;
            try {
                java.math.BigDecimal amount = new java.math.BigDecimal(value.toString());
                if (amount.signum() >= 0) {
                    state.getUsage().merge(key, amount, java.math.BigDecimal::add);
                }
            } catch (NumberFormatException ignored) {
                // 用量仅用于观测，格式错误的用量数据直接丢弃。
            }
        });
        java.math.BigDecimal tokenBudget = decimalPolicy(plan, "tokenBudget");
        java.math.BigDecimal totalTokens = state.getUsage()
                .getOrDefault("totalTokens", java.math.BigDecimal.ZERO);
        if (tokenBudget != null && totalTokens.compareTo(tokenBudget) > 0) {
            return new NodeFailure(WorkflowErrorCode.QUOTA_EXCEEDED.name(),
                    "工作流Token用量超过发布预算", false);
        }
        java.math.BigDecimal costBudget = decimalPolicy(plan, "costBudget");
        java.math.BigDecimal cost = state.getUsage()
                .getOrDefault("cost", java.math.BigDecimal.ZERO);
        if (costBudget != null && cost.compareTo(costBudget) > 0) {
            return new NodeFailure(WorkflowErrorCode.QUOTA_EXCEEDED.name(),
                    "工作流费用用量超过发布预算", false);
        }
        return null;
    }

    private WorkflowRuntimeState restoreState(
            WorkflowExecution execution, WorkflowExecutionPlan plan) {
        WorkflowCheckpoint checkpoint = checkpointMapper.selectLatestSafe(execution.getExecutionId());
        if (checkpoint != null) {
            if (!plan.getContentHash().equals(checkpoint.getPlanHash())) {
                throw new NodeFailure(WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                        "恢复检查点与执行计划不兼容", false);
            }
            try {
                return objectMapper.readValue(checkpoint.getStateJson(), WorkflowRuntimeState.class);
            } catch (Exception e) {
                throw new NodeFailure(WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                        "恢复检查点格式无效", false);
            }
        }
        WorkflowRuntimeState state = new WorkflowRuntimeState();
        try {
            state.setInput(objectMapper.readTree(execution.getInputJson()));
        } catch (Exception e) {
            throw new NodeFailure(WorkflowErrorCode.DEFINITION_INVALID.name(),
                    "执行输入格式无效", false);
        }
        plan.getEntryNodeIds().forEach(nodeId -> enqueue(state, nodeId, "root"));
        return state;
    }

    private ObjectNode contextRoot(
            WorkflowExecution execution,
            WorkflowRuntimeState state,
            String branchPath) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("input", state.getInput());
        ObjectNode nodes = root.putObject("nodes");
        state.getOutputs().forEach((nodeId, output) ->
                nodes.putObject(nodeId).set("output", output));
        ObjectNode environment = root.putObject("env");
        environment.put("name", execution.getEnvironment());
        environment.put("production", "PROD".equals(execution.getEnvironment()));
        ObjectNode executionNode = root.putObject("execution");
        executionNode.put("id", execution.getExecutionId());
        executionNode.put("workflowCode", execution.getWorkflowCode());
        executionNode.put("workflowVersionId", execution.getWorkflowVersionId());
        if (execution.getVersionNo() != null) executionNode.put("versionNo", execution.getVersionNo());
        if (execution.getRootExecutionId() != null) {
            executionNode.put("rootId", execution.getRootExecutionId());
        }
        if (execution.getParentExecutionId() != null) {
            executionNode.put("parentId", execution.getParentExecutionId());
        }
        if (execution.getExecutionDepth() != null) {
            executionNode.put("depth", execution.getExecutionDepth());
        }
        executionNode.put("principalType", execution.getPrincipalType());
        ObjectNode loop = root.putObject("loop");
        loop.put("branchPath", branchPath);
        state.getLoopCounts().forEach(loop::put);
        root.set("approval", state.getApproval() == null
                ? objectMapper.createObjectNode() : state.getApproval());
        return root;
    }

    private Map<String, List<WorkflowExecutionPlan.PlanEdge>> outgoing(
            WorkflowExecutionPlan plan) {
        Map<String, List<WorkflowExecutionPlan.PlanEdge>> result = new HashMap<>();
        plan.getEdges().stream()
                .filter(edge -> !"__start__".equals(edge.getSource()))
                .forEach(edge -> result.computeIfAbsent(edge.getSource(), ignored -> new ArrayList<>())
                        .add(edge));
        return result;
    }

    private Map<String, Integer> incomingCounts(WorkflowExecutionPlan plan) {
        Map<String, Integer> result = new HashMap<>();
        plan.getEdges().stream()
                .filter(edge -> !"LOOP".equals(edge.getKind()))
                .forEach(edge -> result.merge(edge.getTarget(), 1, Integer::sum));
        return result;
    }

    private WorkflowNodeRun newNodeRun(
            WorkflowExecution execution,
            WorkflowExecutionPlan.PlanNode node,
            WorkflowRuntimeState.Token token,
            String nodeRunId,
            int attempt,
            long fencingToken) {
        WorkflowNodeRun run = new WorkflowNodeRun();
        run.setTenantId(execution.getTenantId());
        run.setNodeRunId(nodeRunId);
        run.setExecutionId(execution.getExecutionId());
        run.setNodeId(node.getId());
        run.setBranchPath(token.getBranchPath());
        run.setAttemptNo(attempt);
        run.setHandlerVersion(node.getHandlerVersion());
        run.setStatus("RUNNING");
        run.setSideEffect(node.getSideEffect());
        run.setSideEffectStatus("WRITE".equals(node.getSideEffect()) ? "PENDING" : "NONE");
        run.setIdempotencyKey(execution.getExecutionId() + ":" + nodeRunId);
        run.setInputJson("{}");
        run.setFencingToken(fencingToken);
        run.setCreateTime(new Date());
        run.setUpdateTime(new Date());
        run.setStartTime(new Date());
        return run;
    }

    private boolean hasUnconfirmedWrite(String executionId) {
        Long count = nodeRunMapper.selectCount(new LambdaQueryWrapper<WorkflowNodeRun>()
                .eq(WorkflowNodeRun::getExecutionId, executionId)
                .eq(WorkflowNodeRun::getSideEffect, "WRITE")
                .eq(WorkflowNodeRun::getStatus, "RUNNING"));
        return count != null && count > 0;
    }

    private int prepareAttemptBase(String executionId, String nodeRunId) {
        List<WorkflowNodeRun> existing = nodeRunMapper.selectList(
                new LambdaQueryWrapper<WorkflowNodeRun>()
                        .eq(WorkflowNodeRun::getExecutionId, executionId)
                        .eq(WorkflowNodeRun::getNodeRunId, nodeRunId)
                        .orderByDesc(WorkflowNodeRun::getAttemptNo));
        int maximum = 0;
        for (WorkflowNodeRun run : existing) {
            maximum = Math.max(maximum, run.getAttemptNo());
            if ("RUNNING".equals(run.getStatus()) && !"WRITE".equals(run.getSideEffect())) {
                run.setStatus("FAILED");
                run.setErrorCode(WorkflowErrorCode.EXECUTION_CONFLICT.name());
                run.setErrorMessage("节点运行在安全检查点前失去租约，已由新attempt恢复");
                run.setFinishTime(new Date());
                nodeRunMapper.updateById(run);
            }
        }
        return maximum;
    }

    private int nextAttemptNo(String executionId, String nodeRunId) {
        return nodeRunMapper.selectList(new LambdaQueryWrapper<WorkflowNodeRun>()
                        .eq(WorkflowNodeRun::getExecutionId, executionId)
                        .eq(WorkflowNodeRun::getNodeRunId, nodeRunId)
                        .orderByDesc(WorkflowNodeRun::getAttemptNo)
                        .last("LIMIT 1"))
                .stream()
                .map(WorkflowNodeRun::getAttemptNo)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(0) + 1;
    }

    private WorkflowExecution requireClaim(
            String executionId, String runnerId, long fencingToken) {
        WorkflowExecution execution = executionMapper.selectByExecutionId(executionId);
        if (execution == null || !runnerId.equals(execution.getRunnerId())
                || execution.getFencingToken() == null
                || execution.getFencingToken() != fencingToken
                || !Set.of("RUNNING", "RECOVERING").contains(execution.getStatus())) {
            throw new LeaseLostException();
        }
        return execution;
    }

    private void ensureLease(
            String executionId, String runnerId, long fencingToken, int leaseSeconds) {
        if (executionMapper.renewLease(
                executionId, runnerId, fencingToken, leaseSeconds) != 1) {
            throw new LeaseLostException();
        }
    }

    private boolean cancellationRequested(
            String executionId, String runnerId, long fencingToken) {
        WorkflowExecution execution = executionMapper.selectByExecutionId(executionId);
        return execution == null
                || Boolean.TRUE.equals(execution.getCancelRequested())
                || !runnerId.equals(execution.getRunnerId())
                || execution.getFencingToken() == null
                || execution.getFencingToken() != fencingToken;
    }

    private WorkflowExecutionPlan readPlan(String json) {
        try {
            return objectMapper.readValue(json, WorkflowExecutionPlan.class);
        } catch (Exception e) {
            throw new NodeFailure(WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                    "执行计划格式无效", false);
        }
    }

    private int intPolicy(WorkflowExecutionPlan plan, String name, int defaultValue) {
        Object value = plan.getPolicies().get(name);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private java.math.BigDecimal decimalPolicy(
            WorkflowExecutionPlan plan, String name) {
        Object value = plan.getPolicies().get(name);
        if (value == null) return null;
        try {
            return new java.math.BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new NodeFailure(WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                    "执行计划预算格式无效", false);
        }
    }

    private String logicalNodeRunId(String executionId, String nodeId, String branchPath) {
        return UUID.nameUUIDFromBytes((executionId + "\u0000" + nodeId + "\u0000" + branchPath)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new NodeFailure(WorkflowErrorCode.INTERNAL_ERROR.name(),
                    "工作流运行状态序列化失败", false);
        }
    }

    private String persistedJson(JsonNode value) {
        return writeJson(dataRedactor.redact(value));
    }

    private void ensureInlineSize(String value) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > MAX_INLINE_OUTPUT_BYTES) {
            throw new NodeFailure(WorkflowErrorCode.QUOTA_EXCEEDED.name(),
                    "节点内联输出超过1MB，请使用制品存储", false);
        }
    }

    private String errorCode(Throwable error) {
        if (error instanceof NodeFailure failure) return failure.code;
        return WorkflowErrorCode.INTERNAL_ERROR.name();
    }

    private void validateFrozenOutput(
            WorkflowExecutionPlan.PlanNode node, WorkflowNodeResult result) {
        List<String> errors = frozenOutputErrors(node, result);
        if (!errors.isEmpty()) {
            throw new NodeFailure(
                    WorkflowErrorCode.OUTPUT_SCHEMA_MISMATCH.name(),
                    "节点输出不符合发布契约: "
                            + String.join("；", errors.subList(0, Math.min(errors.size(), 5))),
                    false);
        }
    }

    static List<String> frozenOutputErrors(
            WorkflowExecutionPlan.PlanNode node, WorkflowNodeResult result) {
        if (node.getSchemaSource() == null
                || "NODE_CONTRACT".equals(node.getSchemaSource())
                || node.getOutputSchema() == null
                || node.getOutputSchema().isEmpty()) {
            return List.of();
        }
        JsonNode output = result == null ? null : result.output();
        return OUTPUT_VALIDATOR.validate(
                node.getOutputSchema(), output, "$.nodes." + node.getId() + ".output");
    }

    private boolean canSkip(NodeFailure failure) {
        return !failure.needsAttention
                && !Set.of(
                WorkflowErrorCode.NODE_CANCELLED.name(),
                WorkflowErrorCode.PLAN_INCOMPATIBLE.name(),
                WorkflowErrorCode.SIDE_EFFECT_UNCONFIRMED.name())
                .contains(failure.code);
    }

    private long retryDelay(WorkflowExecutionPlan.PlanNode node, int failedAttemptIndex) {
        if (node.getRetryPolicy() == null) return 0;
        long initial = node.getRetryPolicy().getInitialDelayMs() == null
                ? 0 : node.getRetryPolicy().getInitialDelayMs();
        long maximum = node.getRetryPolicy().getMaxDelayMs() == null
                ? initial : node.getRetryPolicy().getMaxDelayMs();
        if (!"EXPONENTIAL".equals(node.getRetryPolicy().getBackoff())) {
            return Math.min(initial, maximum);
        }
        long multiplier = 1L << Math.min(Math.max(0, failedAttemptIndex - 1), 20);
        return Math.min(maximum, Math.multiplyExact(initial, multiplier));
    }

    private boolean isRetryable(WorkflowExecutionPlan.PlanNode node, String code) {
        if (node.getRetryPolicy() == null || code == null) return false;
        List<String> configured = node.getRetryPolicy().getRetryableErrors();
        WorkflowErrorCode error;
        try {
            error = WorkflowErrorCode.valueOf(code);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (configured == null || configured.isEmpty()) {
            return error.isRetryableByDefault();
        }
        return configured.contains(error.name())
                || configured.contains(error.getCategory().name());
    }

    private void ensureWorkflowDeadline(long workflowDeadline) {
        if (System.currentTimeMillis() >= workflowDeadline) {
            throw new NodeFailure(WorkflowErrorCode.EXECUTION_TIMEOUT.name(),
                    "工作流总执行时间超过上限", false);
        }
    }

    /**
     * 获取可信的工作流开始时间。
     * 数据库与应用时区配置异常时，SQL NOW() 可能写出早于创建时间的开始时间；
     * 开始时间不允许早于创建时间，避免任务被误判为已经超时。
     */
    private long workflowStartedAtMillis(WorkflowExecution execution) {
        long currentTime = System.currentTimeMillis();
        long startedAt = execution.getStartTime() == null
                ? currentTime : execution.getStartTime().getTime();
        if (execution.getCreateTime() == null
                || startedAt >= execution.getCreateTime().getTime()) {
            return startedAt;
        }
        log.warn("Workflow execution {} has start time {} before create time {}, "
                        + "using create time to calculate deadline",
                execution.getExecutionId(), execution.getStartTime(), execution.getCreateTime());
        return execution.getCreateTime().getTime();
    }

    private String safeMessage(Throwable error) {
        String value = error == null || error.getMessage() == null
                ? "节点执行失败" : error.getMessage();
        value = value.replaceAll("(?i)(api[-_ ]?key|authorization|password|token)\\s*[:=]\\s*[^,;\\s]+",
                "$1=[REDACTED]");
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private void finishFailure(
            String executionId, String runnerId, long fencingToken, NodeFailure failure) {
        try {
            if (failure.needsAttention) {
                persistence.needsAttention(executionId, runnerId, fencingToken,
                        failure.code, failure.getMessage());
            } else {
                String status = WorkflowErrorCode.NODE_CANCELLED.name().equals(failure.code)
                        ? "CANCELLED" : "FAILED";
                String event = "CANCELLED".equals(status)
                        ? "EXECUTION_CANCELLED" : "EXECUTION_FAILED";
                persistence.finishExecution(executionId, runnerId, fencingToken,
                        status, null, failure.code, failure.getMessage(), event);
            }
        } catch (Exception fenceLost) {
            log.warn("Unable to persist Workflow failure for {}: {}",
                    executionId, fenceLost.getMessage());
        }
    }

    private static class NodeFailure extends RuntimeException {
        private final String code;
        private final boolean needsAttention;

        NodeFailure(String code, String message, boolean needsAttention) {
            super(message);
            this.code = code;
            this.needsAttention = needsAttention;
        }
    }

    private static class LeaseLostException extends RuntimeException {
    }

    private record NodeExecution(WorkflowNodeResult result, WorkflowNodeRun run) {
    }

    private record ParallelAttempt(
            WorkflowExecutionPlan.PlanNode node,
            WorkflowRuntimeState.Token token,
            WorkflowNodeRun run,
            int maxAttempts,
            long deadlineMillis,
            Future<WorkflowNodeResult> future) {
    }

    private record ParallelOutcome(
            ParallelAttempt attempt,
            WorkflowNodeResult result,
            Exception error) {
    }
}
