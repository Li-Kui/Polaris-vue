package com.polaris.ai.workflow.langgraph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.chat.AiAssistant;
import com.polaris.ai.domain.AiAgent;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.domain.AiWorkflow;
import com.polaris.ai.pivot.AiModelFactory;
import com.polaris.ai.service.IAiAgentService;
import com.polaris.ai.service.IAiWorkflowService;
import com.polaris.ai.tools.SecurityContextToolExecutor;
import com.polaris.ai.tools.base.AiTool;
import com.polaris.ai.utils.AiErrorTranslator;
import com.polaris.ai.workflow.WorkflowNodeExecutor;
import com.polaris.ai.workflow.event.WorkflowSsePublisher;
import com.polaris.ai.workflow.runtime.WorkflowExecutionCancellationProbe;
import com.polaris.ai.workflow.runtime.WorkflowExecutionSnapshot;
import com.polaris.ai.workflow.runtime.WorkflowExecutionStore;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraph4j 图工作流执行引擎（二期闭环实现）
 * <p>
 * 核心设计：
 * <ul>
 *   <li>图调度与持久化：SpringJdbcCheckpointSaver 融入 Spring 事务，拦截并持久化每个步骤</li>
 *   <li>HITL 中断：通过 interruptBefore 拦截并挂起审批节点，配合 /resume 实现挂起流式回复</li>
 *   <li>SSE 健壮性：客户端物理断开连接时，主动感知并抛错阻断 LLM 后台继续执行</li>
 * </ul>
 *
 * @author polaris
 */
@Slf4j
@Service
public class LangGraph4jEngine {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** latestOutput 累积上限（防止循环场景下无限膨胀） */
    private static final int MAX_LATEST_OUTPUT_LENGTH = 30_000;

    @Autowired
    private SpringJdbcCheckpointSaver springJdbcCheckpointSaver;

    @Autowired
    private WorkflowExecutionStore executionStore;

    @Autowired
    private WorkflowSsePublisher eventPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IAiWorkflowService workflowService;

    @Autowired
    private IAiAgentService agentService;

    @Autowired
    private AiModelFactory modelFactory;

    @Autowired
    private com.polaris.ai.mapper.AiChatMapper aiChatMapper;

    @Autowired(required = false)
    private List<AiTool> allTools = new ArrayList<>();

    @Autowired(required = false)
    private List<WorkflowNodeExecutor> javaExecutors = new ArrayList<>();

    @Autowired
    @Qualifier("workflowNodeTaskExecutor")
    private AsyncTaskExecutor nodeTaskExecutor;

    /** 首次运行服务端已创建并固化的执行。 */
    public void run(WorkflowExecutionStore.Execution execution,
                    SecurityContext securityContext, SseEmitter emitter, AtomicBoolean cancelled) {
        String executionId = execution.executionId();
        try {
            if (!executionStore.claimQueued(executionId)) {
                throw new IllegalStateException("工作流执行状态不是 QUEUED");
            }
            WorkflowExecutionCancellationProbe cancellationProbe = cancellationProbe(
                    executionId, cancelled);
            WorkflowExecutionSnapshot snapshot = getOrParseExecutionSnapshot(execution.workflowSnapshot());
            GraphTopology topology = snapshot.getTopology();
            topology.validate();
            validateSnapshotCompatibility(snapshot);
            eventPublisher.send(emitter, executionId, "execution_started", null,
                    Map.of("workflowCode", execution.workflowCode(),
                            "workflowVersion", execution.workflowVersion()));
            ensureNotCancelled(cancelled, cancellationProbe);
            saveWorkflowUserMessage(execution);

            StateGraph<PolarisAgentState> stateGraph = buildStateGraph(
                    topology, snapshot, securityContext, emitter, executionId,
                    cancelled, cancellationProbe);
            var compiledGraph = stateGraph.compile(compileConfig(topology));

            Map<String, Object> initialState = new HashMap<>();
            initialState.put(PolarisAgentState.USER_INPUT, execution.inputText());
            initialState.put(PolarisAgentState.LATEST_OUTPUT, "");
            initialState.put(PolarisAgentState.ROUTE_DECISION, "");
            initialState.put(PolarisAgentState.VARIABLES, new HashMap<>());
            initialState.put(PolarisAgentState.ITERATION_COUNT, 0);

            compiledGraph.invoke(initialState, runnableConfig(execution));
            ensureNotCancelled(cancelled, cancellationProbe);
            finishOrPause(execution, emitter);
        } catch (Exception e) {
            handleException(e, executionId, emitter, cancelled);
        } finally {
            eventPublisher.release(executionId);
        }
    }

    /** 审批通过后，仅使用创建执行时固化的快照恢复。 */
    public void resume(WorkflowExecutionStore.Execution execution, String feedback,
                       SecurityContext securityContext, SseEmitter emitter, AtomicBoolean cancelled) {
        String executionId = execution.executionId();
        try {
            WorkflowExecutionCancellationProbe cancellationProbe = cancellationProbe(
                    executionId, cancelled);
            WorkflowExecutionSnapshot snapshot = getOrParseExecutionSnapshot(execution.workflowSnapshot());
            GraphTopology topology = snapshot.getTopology();
            topology.validate();
            validateSnapshotCompatibility(snapshot);
            eventPublisher.send(emitter, executionId, "execution_resumed", null, Map.of());
            StateGraph<PolarisAgentState> stateGraph = buildStateGraph(
                    topology, snapshot, securityContext, emitter, executionId,
                    cancelled, cancellationProbe);
            var compiledGraph = stateGraph.compile(compileConfig(topology));
            RunnableConfig runConfig = runnableConfig(execution);

            Optional<Checkpoint> lastCheckpoint = springJdbcCheckpointSaver.get(runConfig);
            if (lastCheckpoint.isEmpty()) {
                throw new IllegalStateException("找不到历史检查点，无法恢复");
            }
            Map<String, Object> lastState = lastCheckpoint.get().getState();
            Map<String, Object> stateUpdate = new HashMap<>();
            stateUpdate.put(PolarisAgentState.ITERATION_COUNT,
                    ((Number) lastState.getOrDefault(PolarisAgentState.ITERATION_COUNT, 0)).intValue());
            stateUpdate.put(PolarisAgentState.ROUTE_DECISION, "approved");
            if (feedback != null && !feedback.isBlank()) {
                String previous = Objects.toString(lastState.get(PolarisAgentState.LATEST_OUTPUT), "");
                stateUpdate.put(PolarisAgentState.LATEST_OUTPUT,
                        previous.isBlank() ? feedback : previous + "\n\n[审批意见]\n" + feedback);
            }

            RunnableConfig nextConfig = compiledGraph.updateState(runConfig, stateUpdate);
            compiledGraph.invoke((Map<String, Object>) null, nextConfig);
            ensureNotCancelled(cancelled, cancellationProbe);
            finishOrPause(execution, emitter);
        } catch (Exception e) {
            handleException(e, executionId, emitter, cancelled);
        } finally {
            eventPublisher.release(executionId);
        }
    }

    CompileConfig compileConfig(GraphTopology topology) {
        List<String> interruptNodes = topology.getNodes().stream()
                .filter(GraphTopology.NodeDef::isRequireApproval)
                .map(GraphTopology.NodeDef::getId)
                .toList();
        long calculatedLimit = ((long) topology.getNodes().size() + 1L)
                * ((long) topology.getMaxIterations() + 1L) + 2L;
        int recursionLimit = (int) Math.min(1_000_000L, Math.max(25L, calculatedLimit));
        CompileConfig.Builder builder = CompileConfig.builder()
                .checkpointSaver(springJdbcCheckpointSaver)
                .recursionLimit(recursionLimit);
        if (!interruptNodes.isEmpty()) {
            builder.interruptBefore(interruptNodes.toArray(new String[0]));
        }
        return builder.build();
    }

    private RunnableConfig runnableConfig(WorkflowExecutionStore.Execution execution) {
        RunnableConfig.Builder builder = RunnableConfig.builder()
                .threadId(execution.executionId())
                .putMetadata("workflow_code", execution.workflowCode())
                .putMetadata("workflow_version", execution.workflowVersion())
                .putMetadata("user_id", execution.userId());
        if (execution.conversationId() != null) {
            builder.putMetadata("conversation_id", execution.conversationId());
        }
        return builder.build();
    }

    private void finishOrPause(WorkflowExecutionStore.Execution execution, SseEmitter emitter) {
        String executionId = execution.executionId();
        String nextNodeId = queryLastNextNodeId(executionId);
        if (nextNodeId != null && !"__END__".equalsIgnoreCase(nextNodeId)) {
            String approvalId = executionStore.createApproval(executionId, nextNodeId);
            updateLatestCheckpointStatus(executionId, "paused");
            eventPublisher.send(emitter, executionId, "node_interrupt", nextNodeId,
                    Map.of("approvalId", approvalId, "message", "等待人工审核确认"));
            saveWorkflowAssistantMessage(executionId, execution.conversationId());
        } else {
            String result = queryLatestOutput(executionId);
            if (!executionStore.markSucceeded(executionId, result)) {
                completeEmitter(emitter);
                return;
            }
            updateLatestCheckpointStatus(executionId, "done");
            saveWorkflowAssistantMessage(executionId, execution.conversationId());
            eventPublisher.send(emitter, executionId, "workflow_done", null,
                    Map.of("result", result));
        }
        completeEmitter(emitter);
    }

    private void handleException(Exception e, String executionId,
                                 SseEmitter emitter, AtomicBoolean cancelled) {
        NodeTimeoutException timeout = findCause(e, NodeTimeoutException.class);
        if (timeout != null) {
            log.error(">>> [LangGraph4j] 工作流节点超时，executionId: {}, nodeId: {}",
                    executionId, timeout.getNodeId());
            String message = timeout.getMessage();
            if (executionStore.markFailed(executionId, message)) {
                updateLatestCheckpointStatus(executionId, "error");
                try {
                    eventPublisher.send(emitter, executionId, "error", timeout.getNodeId(),
                            Map.of("message", message));
                } catch (Exception ignored) {
                }
            }
            completeEmitter(emitter);
            return;
        }
        boolean streamClosed = isStreamClosed(e) || cancelled.get();
        if (streamClosed) {
            log.warn(">>> [LangGraph4j] 工作流已取消或连接断开，executionId: {}", executionId);
            cancelled.set(true);
            executionStore.markCancelledIfOwned(executionId);
        } else {
            log.error(">>> [LangGraph4j] 工作流运行异常，executionId: {}", executionId, e);
            String message = AiErrorTranslator.translate(e);
            if (executionStore.markFailed(executionId, message)) {
                updateLatestCheckpointStatus(executionId, "error");
                try {
                    eventPublisher.send(emitter, executionId, "error", null,
                            Map.of("message", message));
                } catch (Exception ignored) {
                }
            }
        }
        completeEmitter(emitter);
    }

    private void saveWorkflowUserMessage(WorkflowExecutionStore.Execution execution) {
        if (execution.conversationId() == null) {
            return;
        }
        com.polaris.ai.domain.AiMessage userMsg = new com.polaris.ai.domain.AiMessage();
        userMsg.setConversationId(execution.conversationId());
        userMsg.setWorkflowExecutionId(execution.executionId());
        userMsg.setRole("user");
        userMsg.setContent(execution.inputText());
        userMsg.setFileUrl(execution.fileUrl());
        aiChatMapper.insertMessage(userMsg);

        com.polaris.ai.domain.AiConversation conversation =
                aiChatMapper.selectConversationById(execution.conversationId(), execution.userId());
        if (conversation != null && "新对话".equals(conversation.getTitle())) {
            String input = execution.inputText();
            String title = input.length() > 15 ? input.substring(0, 15) + "…" : input;
            aiChatMapper.updateConversationTitle(execution.conversationId(), title, execution.userId());
        }
    }

    private String queryLatestOutput(String executionId) {
        try {
            String stateJson = jdbcTemplate.queryForObject(
                    "SELECT state_json FROM ai_graph_checkpoint WHERE execution_id = ? " +
                            "ORDER BY sequence_no DESC LIMIT 1",
                    String.class, executionId);
            if (stateJson == null) {
                return "";
            }
            Map<String, Object> state = objectMapper.readValue(stateJson, Map.class);
            return Objects.toString(state.get(PolarisAgentState.LATEST_OUTPUT), "");
        } catch (Exception e) {
            log.warn(">>> [LangGraph4j] 读取最终输出失败，executionId: {}", executionId, e);
            return "";
        }
    }

    private void updateLatestCheckpointStatus(String executionId, String status) {
        jdbcTemplate.update(
                "UPDATE ai_graph_checkpoint SET status = ?, update_time = NOW() " +
                        "WHERE execution_id = ? ORDER BY sequence_no DESC LIMIT 1",
                status, executionId);
    }

    private void ensureNotCancelled(AtomicBoolean cancelled) {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("工作流已取消");
        }
    }

    private void ensureNotCancelled(
            AtomicBoolean cancelled, BooleanSupplier cancellationProbe) {
        if (cancelled.get() || Thread.currentThread().isInterrupted()
                || (cancellationProbe != null && cancellationProbe.getAsBoolean())) {
            throw new CancellationException("工作流已取消或租约已失效");
        }
    }

    private void ensureNotCancelled(
            AtomicBoolean workflowCancelled, AtomicBoolean nodeCancelled,
            BooleanSupplier cancellationProbe) {
        if (isCancelled(workflowCancelled, nodeCancelled, cancellationProbe)) {
            throw new CancellationException("工作流节点已取消");
        }
    }

    private boolean isCancelled(
            AtomicBoolean workflowCancelled, AtomicBoolean nodeCancelled,
            BooleanSupplier cancellationProbe) {
        return Thread.currentThread().isInterrupted()
                || workflowCancelled.get() || nodeCancelled.get()
                || (cancellationProbe != null && cancellationProbe.getAsBoolean());
    }

    private WorkflowExecutionCancellationProbe cancellationProbe(
            String executionId, AtomicBoolean cancelled) {
        return new WorkflowExecutionCancellationProbe(
                executionStore, executionId, cancelled, Duration.ofMillis(250));
    }

    private <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean isStreamClosed(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.util.concurrent.CancellationException
                    || "SSE_CONNECTION_LOST".equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void completeEmitter(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    /**
     * 生成工作流的 Mermaid 拓扑图
     */
    public String generateMermaid(String workflowCode) {
        AiWorkflow workflow = workflowService.selectWorkflowByCode(workflowCode);
        if (workflow == null) {
            return "graph LR\n  ERROR[工作流不存在]";
        }

        String graphJson = workflow.getGraphJson();
        if (graphJson == null || graphJson.trim().isEmpty()) {
            if (workflow.getNodes() != null) {
                graphJson = convertNodesToGraphJson(workflow.getNodes());
            } else {
                return "graph LR\n  ERROR[未配置节点]";
            }
        }

        try {
            GraphTopology topology = objectMapper.readValue(graphJson, GraphTopology.class);
            return buildMermaid(topology);
        } catch (Exception e) {
            log.error("生成 Mermaid 失败", e);
            return "graph LR\n  ERROR[解析失败]";
        }
    }

    public List<Map<String, String>> listJavaExecutors() {
        if (javaExecutors == null) {
            return List.of();
        }
        return javaExecutors.stream()
                .map(executor -> Map.of(
                        "code", executor.getNodeCode(),
                        "name", executor.getNodeName()))
                .sorted(Comparator.comparing(item -> item.get("code")))
                .toList();
    }

    // ================================================================
    //  私有方法：图构建
    // ================================================================

    /**
     * 构建 StateGraph，注册所有节点和边
     */
    private StateGraph<PolarisAgentState> buildStateGraph(
            GraphTopology topology, WorkflowExecutionSnapshot snapshot,
            SecurityContext securityContext, SseEmitter emitter,
            String executionId, AtomicBoolean cancelled,
            BooleanSupplier cancellationProbe) throws Exception {

        StateGraph<PolarisAgentState> graph = new StateGraph<>(PolarisAgentState.SCHEMA, PolarisAgentState::new);
        int maxIterations = topology.getMaxIterations();

        // 注册节点
        for (GraphTopology.NodeDef nodeDef : topology.getNodes()) {
            NodeAction<PolarisAgentState> action = createNodeAction(
                    nodeDef, snapshot, securityContext, emitter, executionId,
                    cancelled, cancellationProbe, maxIterations);
            graph.addNode(nodeDef.getId(), node_async(action));
        }

        // 按源节点分组处理边
        Map<String, List<GraphTopology.EdgeDef>> edgesByFrom = topology.getEdges().stream()
                .collect(Collectors.groupingBy(GraphTopology.EdgeDef::getFrom));

        for (Map.Entry<String, List<GraphTopology.EdgeDef>> entry : edgesByFrom.entrySet()) {
            String from = entry.getKey();
            List<GraphTopology.EdgeDef> edges = entry.getValue();

            // 检查是否有条件边
            boolean hasCondition = edges.stream().anyMatch(e -> e.getCondition() != null && !e.getCondition().isEmpty());

            if (hasCondition) {
                // 条件路由：addConditionalEdges
                Map<String, String> routeMap = new HashMap<>();
                for (GraphTopology.EdgeDef edge : edges) {
                    String condition = edge.getCondition() != null ? edge.getCondition() : "__default__";
                    String target = "__end__".equals(edge.getTo()) ? END : edge.getTo();
                    routeMap.put(condition, target);
                }

                if (!routeMap.containsKey("__default__")) {
                    routeMap.put("__default__", END);
                }
                routeMap.put("__iteration_limit__", END);

                String fromNode = "__start__".equals(from) ? START : from;
                graph.addConditionalEdges(fromNode,
                        edge_async(state -> {
                            String decision = state.routeDecision();
                            if (routeMap.containsKey(decision)) {
                                return decision;
                            }
                            log.warn(">>> 条件路由未匹配: {}，走默认兜底", decision);
                            return "__default__";
                        }),
                        routeMap
                );
            } else {
                // 普通边
                GraphTopology.EdgeDef edge = edges.get(0);
                String fromNode = "__start__".equals(from) ? START : from;
                String toNode = "__end__".equals(edge.getTo()) ? END : edge.getTo();
                graph.addEdge(fromNode, toNode);
            }
        }

        return graph;
    }

    /**
     * 根据节点定义创建对应的 NodeAction
     */
    private NodeAction<PolarisAgentState> createNodeAction(
            GraphTopology.NodeDef nodeDef, WorkflowExecutionSnapshot snapshot,
            SecurityContext securityContext, SseEmitter emitter,
            String executionId, AtomicBoolean cancelled,
            BooleanSupplier cancellationProbe, int maxIterations) {

        String nodeType = nodeDef.getType();
        String ref = nodeDef.getRef();
        int timeoutSeconds = nodeDef.getTimeoutSeconds();

        AtomicBoolean nodeCancelled = new AtomicBoolean(false);
        NodeAction<PolarisAgentState> delegate;
        if ("agent".equals(nodeType)) {
            delegate = createAgentNodeAction(nodeDef.getId(), ref, snapshot,
                    securityContext, emitter, executionId, cancelled, nodeCancelled,
                    cancellationProbe, timeoutSeconds);
        } else if ("classifier".equals(nodeType)) {
            delegate = createClassifierNodeAction(
                    nodeDef, snapshot, securityContext, emitter, executionId,
                    cancelled, nodeCancelled, cancellationProbe,
                    timeoutSeconds, maxIterations);
        } else if ("java".equals(nodeType)) {
            WorkflowNodeExecutor executor = findJavaExecutor(ref);
            if (executor == null) {
                throw new IllegalArgumentException("找不到 Java 节点执行器: " + ref);
            }
            delegate = createJavaNodeAction(
                    nodeDef.getId(), executor, emitter, executionId, cancelled,
                    nodeCancelled, cancellationProbe);
        } else {
            throw new IllegalArgumentException("未知的节点类型: " + nodeType);
        }
        return state -> executeNodeWithTimeout(
                nodeDef.getId(), timeoutSeconds, delegate, state,
                securityContext, emitter, executionId, cancelled, nodeCancelled,
                cancellationProbe);
    }

    Map<String, Object> executeNodeWithTimeout(
            String nodeId, int timeoutSeconds, NodeAction<PolarisAgentState> delegate,
            PolarisAgentState state, SecurityContext securityContext, SseEmitter emitter,
            String executionId, AtomicBoolean cancelled, AtomicBoolean nodeCancelled) throws Exception {
        return executeNodeWithTimeout(
                nodeId, timeoutSeconds, delegate, state, securityContext, emitter,
                executionId, cancelled, nodeCancelled, null);
    }

    Map<String, Object> executeNodeWithTimeout(
            String nodeId, int timeoutSeconds, NodeAction<PolarisAgentState> delegate,
            PolarisAgentState state, SecurityContext securityContext, SseEmitter emitter,
            String executionId, AtomicBoolean cancelled, AtomicBoolean nodeCancelled,
            BooleanSupplier cancellationProbe) throws Exception {
        ensureNotCancelled(cancelled, nodeCancelled, cancellationProbe);
        Future<Map<String, Object>> future = nodeTaskExecutor.submit(() -> {
            SecurityContext previousContext = SecurityContextHolder.getContext();
            try {
                SecurityContextHolder.setContext(securityContext);
                return delegate.apply(state);
            } finally {
                SecurityContextHolder.setContext(previousContext);
            }
        });
        long deadlineNanos = System.nanoTime()
                + java.util.concurrent.TimeUnit.SECONDS.toNanos(timeoutSeconds);
        try {
            while (true) {
                ensureNotCancelled(cancelled, nodeCancelled, cancellationProbe);
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0L) {
                    throw new TimeoutException("node deadline exceeded");
                }
                long waitNanos = Math.min(
                        remainingNanos, java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(250));
                try {
                    return future.get(waitNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
                } catch (TimeoutException ignored) {
                    // 以短周期检查数据库终态，实现跨实例快速取消。
                }
            }
        } catch (TimeoutException e) {
            nodeCancelled.set(true);
            future.cancel(true);
            NodeTimeoutException timeout = new NodeTimeoutException(nodeId, timeoutSeconds);
            try {
                eventPublisher.send(emitter, executionId, "node_error", nodeId,
                        Map.of("message", timeout.getMessage()));
            } catch (Exception ignored) {
            }
            throw timeout;
        } catch (CancellationException e) {
            nodeCancelled.set(true);
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            nodeCancelled.set(true);
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new CancellationException("工作流执行已取消");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(cause);
        }
    }

    /**
     * 创建 Agent 节点的 NodeAction
     */
    private NodeAction<PolarisAgentState> createAgentNodeAction(
            String nodeId, String agentCode, WorkflowExecutionSnapshot snapshot,
            SecurityContext securityContext, SseEmitter emitter, String executionId,
            AtomicBoolean cancelled, AtomicBoolean nodeCancelled,
            BooleanSupplier cancellationProbe, int timeoutSeconds) {

        return state -> {
            ensureNotCancelled(cancelled, nodeCancelled, cancellationProbe);
            WorkflowExecutionSnapshot.AgentSnapshot agent = snapshot.getAgents().get(agentCode);
            if (agent == null) {
                if (snapshot.getSchemaVersion() >= 3) {
                    throw new IllegalStateException("执行快照缺少智能体配置: " + agentCode);
                }
                AiAgent liveAgent = agentService.selectAgentByCode(agentCode);
                if (liveAgent == null) {
                    throw new RuntimeException("找不到智能体: " + agentCode);
                }
                agent = WorkflowExecutionSnapshot.AgentSnapshot.from(
                        liveAgent, liveAgent.getModelConfigId());
            }
            WorkflowExecutionSnapshot.AgentSnapshot resolvedAgent = agent;

            String nodeCode = nodeId;
            log.info(">>> [LangGraph4j] 执行 AI 智能体节点: {} -> {} ({})",
                    nodeId, agentCode, agent.getAgentName());

            eventPublisher.send(emitter, executionId, "node_start", nodeCode,
                    Map.of("nodeName", resolvedAgent.getAgentName(), "agentCode", agentCode));

            SecurityContext previousContext = SecurityContextHolder.getContext();
            try {
                SecurityContextHolder.setContext(securityContext);

                StreamingChatModel chatModel = getSnapshotStreamingModel(
                        snapshot, agent.getModelConfigId(), agent.getTemperature(), timeoutSeconds);

                String searchKey = null;
                try {
                    AiModelConfig modelConfig = modelFactory.getModelConfig(resolvedAgent.getModelConfigId());
                    if (modelConfig != null) {
                        searchKey = modelConfig.getSearchKey();
                    }
                } catch (Exception e) {
                    log.warn(">>> 读取模型联网搜索配置失败: {}", e.getMessage());
                }

                Map<ToolSpecification, ToolExecutor> activeTools =
                        SecurityContextToolExecutor.getFilteredTools(
                                allTools, resolvedAgent.getTools(), securityContext, searchKey, emitter, nodeCode,
                                eventPublisher, executionId, cancelled, nodeCancelled,
                                cancellationProbe);

                AiAssistant assistant = AiServices.builder(AiAssistant.class)
                        .streamingChatModel(chatModel)
                        .systemMessageProvider(ctx -> resolvedAgent.getSystemPrompt())
                        .tools(activeTools)
                        .build();

                String input;
                if (!state.latestOutput().isEmpty()) {
                    input = String.format("【原始用户请求】: %s\n【截至上一步已获取的信息与中间进度结果】: %s\n【当前任务】: 请结合上述已有的信息与原始请求，继续推进执行。若任务已全部解决，请给出最终总结答复；若仍需调用工具或其它智能体，请自主发起调用或请求。",
                            state.userInput(), state.latestOutput());
                } else {
                    input = state.userInput();
                }
                List<ChatMessage> history = new ArrayList<>();
                history.add(UserMessage.from(input));

                CountDownLatch latch = new CountDownLatch(1);
                StringBuilder fullReply = new StringBuilder();
                AtomicReference<Throwable> streamError = new AtomicReference<>();

                TokenStream tokenStream = assistant.chat(history);
                tokenStream.onPartialResponse(token -> {
                            if (isCancelled(cancelled, nodeCancelled, cancellationProbe)) {
                                return;
                            }
                            try {
                                fullReply.append(token);
                                eventPublisher.send(emitter, executionId, "node_chunk", nodeCode,
                                        Map.of("text", token));
                            } catch (Exception e) {
                                streamError.compareAndSet(null,
                                        new RuntimeException("SSE_CONNECTION_LOST", e));
                                cancelled.set(true);
                                latch.countDown();
                            }
                        })
                        .onPartialThinking(thinking -> {
                            if (!isCancelled(cancelled, nodeCancelled, cancellationProbe)
                                    && thinking != null && thinking.text() != null) {
                                try {
                                    eventPublisher.send(emitter, executionId, "node_thinking", nodeCode,
                                            Map.of("text", thinking.text()));
                                } catch (Exception e) {
                                    streamError.compareAndSet(null, e);
                                    cancelled.set(true);
                                    latch.countDown();
                                }
                            }
                        })
                        .onCompleteResponse(response -> {
                            if (isCancelled(cancelled, nodeCancelled, cancellationProbe)) {
                                latch.countDown();
                                return;
                            }
                            try {
                                eventPublisher.send(emitter, executionId, "node_done", nodeCode, Map.of());
                            } catch (Exception e) {
                                streamError.compareAndSet(null, e);
                            }
                            latch.countDown();
                        })
                        .onError(error -> {
                            if (isCancelled(cancelled, nodeCancelled, cancellationProbe)) {
                                latch.countDown();
                                return;
                            }
                            log.error("[LangGraph4j] 智能体节点 [{}] 流式报错", nodeCode, error);
                            streamError.compareAndSet(null, error);
                            try {
                                eventPublisher.send(emitter, executionId, "node_error", nodeCode,
                                        Map.of("message", AiErrorTranslator.translate(error)));
                            } catch (Exception ignored) {
                            }
                            latch.countDown();
                        })
                        .start();

                latch.await();

                if (streamError.get() != null) {
                    Throwable error = streamError.get();
                    if (error instanceof Exception exception) {
                        throw exception;
                    }
                    throw new RuntimeException(error);
                }
                ensureNotCancelled(cancelled, nodeCancelled, cancellationProbe);

                String output = fullReply.toString().trim();

                // 容错防呆：大模型有时会自作聪明地在 /profile 前面加上奇奇怪怪的外部域名（如 https://www.klingai.com），或者把 /profile 翻译成 /pro文档生成 等，在此自动将其纠正
                if (output != null) {
                    output = output.replaceAll("https?://[^/]+(?=/profile/upload/|/pro[^/]*/upload/)", "")
                                   .replaceAll("\\(/pro[^/]*/upload/", "(/profile/upload/")
                                   .replaceAll("\"/pro[^/]*/upload/", "\"/profile/upload/");
                }

                Map<String, Object> updates = new HashMap<>();
                String currentLatest = state.latestOutput();
                String newOutput = currentLatest;
                if (output != null && !output.trim().isEmpty()) {
                    newOutput = currentLatest.isEmpty() ? output : (currentLatest + "\n\n" + output);
                }
                newOutput = capLatestOutput(newOutput, MAX_LATEST_OUTPUT_LENGTH);
                updates.put(PolarisAgentState.LATEST_OUTPUT, newOutput);
                updates.put(PolarisAgentState.ROUTE_DECISION, "");

                Map<String, Object> vars = new HashMap<>(state.variables());
                vars.put(nodeCode + "_output", output);
                updates.put(PolarisAgentState.VARIABLES, vars);

                return updates;

            } finally {
                SecurityContextHolder.setContext(previousContext);
            }
        };
    }

    /**
     * 创建分类路由节点的 NodeAction
     * <p>
     * 用一次受约束的 LLM 调用，从用户定义的分支中选出最匹配的一个，输出 slug 作为路由决策。
     * 分类节点不产出业务正文，latestOutput 透传保持不变，避免污染下游业务节点的输入。
     */
    private NodeAction<PolarisAgentState> createClassifierNodeAction(
            GraphTopology.NodeDef nodeDef, WorkflowExecutionSnapshot snapshot,
            SecurityContext securityContext, SseEmitter emitter,
            String executionId, AtomicBoolean cancelled, AtomicBoolean nodeCancelled,
            BooleanSupplier cancellationProbe, int timeoutSeconds, int maxIterations) {

        return state -> {
            ensureNotCancelled(cancelled, nodeCancelled, cancellationProbe);
            String nodeId = nodeDef.getId();
            List<GraphTopology.BranchDef> branches = nodeDef.getBranches();
            log.info(">>> [LangGraph4j] 执行分类路由节点: {}", nodeId);

            eventPublisher.send(emitter, executionId, "node_start", nodeId,
                    Map.of("nodeName", "意图分类"));

            if (branches == null || branches.isEmpty()) {
                throw new RuntimeException("分类节点 " + nodeId + " 未定义任何分支出口");
            }

            Object rawVisits = state.variables().get(classifierVisitKey(nodeId));
            int previousVisits = rawVisits instanceof Number number ? number.intValue() : 0;
            if (classifierLimitReached(previousVisits, maxIterations)) {
                log.warn(">>> [LangGraph4j] 分类节点 {} 已完成 {} 次判定，达到循环上限 {}，强制终止",
                        nodeId, previousVisits, maxIterations);
                Map<String, Object> updates = new HashMap<>();
                updates.put(PolarisAgentState.ROUTE_DECISION, "__iteration_limit__");
                eventPublisher.send(emitter, executionId, "node_route", nodeId,
                        Map.of("route", "__iteration_limit__"));
                eventPublisher.send(emitter, executionId, "node_done", nodeId, Map.of());
                return updates;
            }

            SecurityContext previousContext = SecurityContextHolder.getContext();
            try {
                SecurityContextHolder.setContext(securityContext);

                // 1. 选模型：指定了 modelConfigId 用指定的，否则用系统默认聊天模型
                StreamingChatModel chatModel = getSnapshotStreamingModel(
                        snapshot, nodeDef.getModelConfigId(), null, timeoutSeconds);

                // 2. 构造系统固定的分类指令（用户不可见）
                StringBuilder optionsText = new StringBuilder();
                for (GraphTopology.BranchDef b : branches) {
                    optionsText.append("- ").append(b.getSlug()).append("：").append(b.getLabel()).append("\n");
                }
                String classifierSystemPrompt =
                        "你是一个严格的意图分类器。请根据用户输入，从下列选项中选择唯一最匹配的一个。\n" +
                        "只允许输出选项的标识符本身（即冒号前的英文标识），不要输出任何解释、标点或多余文字。\n\n" +
                        "可选分类：\n" + optionsText;

                // 3. 分类的输入：原始用户请求 +（若有）上游输出（截取尾部防止超长）
                String upstreamForClassify = state.latestOutput();
                if (upstreamForClassify.length() > 4000) {
                    upstreamForClassify = "...(前文已省略)...\n"
                            + upstreamForClassify.substring(upstreamForClassify.length() - 4000);
                }
                String classifyInput = !upstreamForClassify.isEmpty()
                        ? ("用户请求：" + state.userInput() + "\n上游信息：" + upstreamForClassify)
                        : ("用户请求：" + state.userInput());

                AiAssistant assistant = AiServices.builder(AiAssistant.class)
                        .streamingChatModel(chatModel)
                        .systemMessageProvider(ctx -> classifierSystemPrompt)
                        .build();

                List<ChatMessage> history = new ArrayList<>();
                history.add(UserMessage.from(classifyInput));

                CountDownLatch latch = new CountDownLatch(1);
                StringBuilder reply = new StringBuilder();
                AtomicReference<Throwable> err = new AtomicReference<>();

                assistant.chat(history)
                        .onPartialResponse(token -> {
                            if (!isCancelled(cancelled, nodeCancelled, cancellationProbe)) {
                                reply.append(token);
                            }
                        })
                        .onCompleteResponse(r -> latch.countDown())
                        .onError(e -> {
                            if (!isCancelled(cancelled, nodeCancelled, cancellationProbe)) {
                                err.compareAndSet(null, e);
                            }
                            latch.countDown();
                        })
                        .start();

                latch.await();
                ensureNotCancelled(cancelled, nodeCancelled, cancellationProbe);
                if (err.get() != null) {
                    Throwable error = err.get();
                    if (error instanceof Exception exception) {
                        throw exception;
                    }
                    throw new RuntimeException(error);
                }

                // 4. 清洗输出为纯标识，并匹配已定义的分支 slug
                String raw = reply.toString().trim().replaceAll("[\\p{Punct}&&[^_-]]", "").trim();
                String decision = "";
                for (GraphTopology.BranchDef b : branches) {
                    if (raw.equals(b.getSlug())) {
                        decision = b.getSlug();
                        break;
                    }
                }
                // 精确匹配失败时做一次包含匹配兜底（按 slug 长度降序，优先匹配更长的标识）
                if (decision.isEmpty()) {
                    String bestMatch = "";
                    for (GraphTopology.BranchDef b : branches) {
                        if (b.getSlug() != null && raw.contains(b.getSlug())
                                && b.getSlug().length() > bestMatch.length()) {
                            bestMatch = b.getSlug();
                        }
                    }
                    decision = bestMatch;
                }

                log.info(">>> [LangGraph4j] 分类节点 [{}] 判定结果: raw='{}', decision='{}'", nodeId, raw, decision);

                Map<String, Object> updates = new HashMap<>();
                // 关键：不覆盖 latestOutput，透传给下游业务节点；仅写路由决策
                updates.put(PolarisAgentState.ROUTE_DECISION, decision);
                updates.put(PolarisAgentState.ITERATION_COUNT, state.iterationCount() + 1);
                Map<String, Object> variables = new HashMap<>(state.variables());
                variables.put(classifierVisitKey(nodeId), previousVisits + 1);
                updates.put(PolarisAgentState.VARIABLES, variables);

                eventPublisher.send(emitter, executionId, "node_route", nodeId,
                        Map.of("route", decision));
                eventPublisher.send(emitter, executionId, "node_done", nodeId, Map.of());
                return updates;

            } finally {
                SecurityContextHolder.setContext(previousContext);
            }
        };
    }

    /**
     * 创建 Java 节点的 NodeAction
     */
    private NodeAction<PolarisAgentState> createJavaNodeAction(
            String nodeId, WorkflowNodeExecutor executor, SseEmitter emitter,
            String executionId, AtomicBoolean cancelled, AtomicBoolean nodeCancelled,
            BooleanSupplier cancellationProbe) {

        return state -> {
            ensureNotCancelled(cancelled, nodeCancelled, cancellationProbe);
            String nodeCode = nodeId;
            eventPublisher.send(emitter, executionId, "node_start", nodeCode,
                    Map.of("nodeName", "系统任务", "executorCode", executor.getNodeCode()));

            try {
                NodeAction<PolarisAgentState> adapted =
                        WorkflowNodeExecutorAdapter.adapt(nodeId, executor, emitter);
                Map<String, Object> result = adapted.apply(state);
                ensureNotCancelled(cancelled, nodeCancelled, cancellationProbe);

                eventPublisher.send(emitter, executionId, "node_done", nodeCode, Map.of());

                Map<String, Object> updates = new HashMap<>(result);
                updates.put(PolarisAgentState.ROUTE_DECISION, ""); // 运行完毕后自动重置上一轮路由决策残留，防止污染其它条件路由
                return updates;

            } catch (Exception e) {
                if (isCancelled(cancelled, nodeCancelled, cancellationProbe)) {
                    throw new CancellationException("工作流节点已取消");
                }
                log.error("[LangGraph4j] Java 节点 [{}] 执行异常", nodeCode, e);
                eventPublisher.send(emitter, executionId, "node_error", nodeCode,
                        Map.of("message", AiErrorTranslator.translate(e)));
                throw e;
            }
        };
    }

    // ================================================================
    //  私有工具与数据转换方法
    // ================================================================

    WorkflowExecutionSnapshot getOrParseExecutionSnapshot(String snapshotJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(snapshotJson);
            if (root.has("topology")) {
                WorkflowExecutionSnapshot snapshot =
                        objectMapper.treeToValue(root, WorkflowExecutionSnapshot.class);
                int schemaVersion = root.has("schemaVersion")
                        ? root.path("schemaVersion").asInt(-1) : 2;
                if (schemaVersion < 1
                        || schemaVersion > WorkflowExecutionSnapshot.CURRENT_SCHEMA_VERSION) {
                    throw new IllegalArgumentException("不支持的执行快照版本: " + schemaVersion);
                }
                snapshot.setSchemaVersion(schemaVersion);
                if (snapshot.getAgents() == null) {
                    snapshot.setAgents(new LinkedHashMap<>());
                }
                if (snapshot.getModels() == null) {
                    snapshot.setModels(new LinkedHashMap<>());
                }
                if (snapshot.getJavaExecutors() == null) {
                    snapshot.setJavaExecutors(new LinkedHashMap<>());
                }
                if (snapshot.getTools() == null) {
                    snapshot.setTools(new LinkedHashMap<>());
                }
                if (snapshot.getTopology() == null) {
                    throw new IllegalArgumentException("执行快照缺少 topology");
                }
                if (schemaVersion < WorkflowExecutionSnapshot.CURRENT_SCHEMA_VERSION) {
                    log.warn(">>> [LangGraph4j] 使用旧版执行快照 schema v{}，仅提供兼容恢复",
                            schemaVersion);
                }
                return snapshot;
            }
            WorkflowExecutionSnapshot legacy = new WorkflowExecutionSnapshot();
            legacy.setSchemaVersion(1);
            legacy.setTopology(objectMapper.treeToValue(root, GraphTopology.class));
            return legacy;
        } catch (Exception e) {
            throw new RuntimeException("工作流执行快照解析失败: " + e.getMessage(), e);
        }
    }

    void validateSnapshotCompatibility(WorkflowExecutionSnapshot snapshot) {
        if (snapshot.getSchemaVersion() < 3) {
            return;
        }
        for (GraphTopology.NodeDef node : snapshot.getTopology().getNodes()) {
            if ("java".equals(node.getType())) {
                verifyImplementation("Java 节点执行器", node.getRef(),
                        snapshot.getJavaExecutors().get(node.getRef()),
                        findJavaExecutor(node.getRef()));
            } else if ("agent".equals(node.getType())) {
                WorkflowExecutionSnapshot.AgentSnapshot agent =
                        snapshot.getAgents().get(node.getRef());
                if (agent == null) {
                    throw new IllegalStateException("执行快照缺少智能体配置: " + node.getRef());
                }
                if (agent.getModelConfigId() == null
                        || !snapshot.getModels().containsKey(agent.getModelConfigId())) {
                    throw new IllegalStateException("执行快照缺少智能体模型配置: " + node.getRef());
                }
                for (String toolName :
                        SecurityContextToolExecutor.resolveToolClassNames(agent.getTools())) {
                    verifyImplementation("AI 工具", toolName,
                            snapshot.getTools().get(toolName), findAiTool(toolName));
                }
            } else if ("classifier".equals(node.getType())) {
                if (node.getModelConfigId() == null
                        || !snapshot.getModels().containsKey(node.getModelConfigId())) {
                    throw new IllegalStateException("执行快照缺少分类节点模型配置: " + node.getId());
                }
            }
        }
    }

    private void verifyImplementation(
            String type, String key,
            WorkflowExecutionSnapshot.ImplementationSnapshot expected,
            Object current) {
        if (expected == null) {
            throw new IllegalStateException("执行快照缺少" + type + "指纹: " + key);
        }
        if (current == null) {
            throw new IllegalStateException(type + "已不存在: " + key);
        }
        if (!expected.matches(current)) {
            throw new IllegalStateException(type + "实现已变化，不能恢复旧执行: " + key);
        }
    }

    private StreamingChatModel getSnapshotStreamingModel(
            WorkflowExecutionSnapshot snapshot, Long modelConfigId,
            Double temperatureOverride, int timeoutSeconds) {
        if (modelConfigId == null) {
            if (snapshot.getSchemaVersion() >= 3) {
                throw new IllegalStateException("执行快照缺少模型配置 ID");
            }
            return modelFactory.getDefaultStreamingModel();
        }
        WorkflowExecutionSnapshot.ModelSnapshot modelSnapshot =
                snapshot.getModels().get(modelConfigId);
        if (modelSnapshot == null) {
            if (snapshot.getSchemaVersion() >= 3) {
                throw new IllegalStateException("执行快照缺少模型配置: " + modelConfigId);
            }
            return modelFactory.getStreamingModel(modelConfigId);
        }
        AiModelConfig liveConfig = modelFactory.getModelConfig(modelConfigId);
        if (liveConfig == null) {
            throw new IllegalStateException(
                    "模型密钥配置已不存在，无法执行快照模型: " + modelConfigId);
        }
        if (liveConfig.getStatus() != null && !"1".equals(liveConfig.getStatus())) {
            throw new IllegalStateException("模型配置已停用，无法继续执行: " + modelConfigId);
        }
        AiModelConfig merged = modelSnapshot.mergeSecrets(liveConfig, temperatureOverride);
        return modelFactory.getWorkflowStreamingModel(
                merged, snapshotFingerprint(modelSnapshot, temperatureOverride, timeoutSeconds),
                Duration.ofSeconds(timeoutSeconds));
    }

    private String snapshotFingerprint(
            WorkflowExecutionSnapshot.ModelSnapshot modelSnapshot,
            Double temperatureOverride, int timeoutSeconds) {
        try {
            String material = objectMapper.writeValueAsString(modelSnapshot)
                    + "|temperature=" + Objects.toString(temperatureOverride, "")
                    + "|timeoutSeconds=" + timeoutSeconds;
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (Exception e) {
            throw new IllegalStateException("无法计算模型快照指纹", e);
        }
    }

    private String classifierVisitKey(String nodeId) {
        return "__classifier_visits__:" + nodeId;
    }

    /**
     * 截取 latestOutput，防止循环场景下无限累积。
     * 保留尾部最近内容，在段落边界截断。
     */
    static String capLatestOutput(String accumulated, int maxLength) {
        if (accumulated == null || accumulated.length() <= maxLength) {
            return accumulated;
        }
        String truncated = accumulated.substring(accumulated.length() - maxLength);
        int boundary = truncated.indexOf("\n\n");
        if (boundary > 0 && boundary < maxLength / 4) {
            truncated = truncated.substring(boundary + 2);
        }
        return "...(前文已省略)...\n\n" + truncated;
    }

    static boolean classifierLimitReached(int completedVisits, int maxIterations) {
        return completedVisits >= maxIterations;
    }

    @SuppressWarnings("unchecked")
    public String convertNodesToGraphJson(String nodesJson) {
        try {
            List<Object> rawList = objectMapper.readValue(nodesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Object.class));

            GraphTopology topology = new GraphTopology();
            List<GraphTopology.NodeDef> nodes = new ArrayList<>();
            List<GraphTopology.EdgeDef> edges = new ArrayList<>();
            List<String> nodeCodes = new ArrayList<>();

            for (Object item : rawList) {
                if (item instanceof String) {
                    String code = (String) item;
                    GraphTopology.NodeDef nodeDef = new GraphTopology.NodeDef();
                    nodeDef.setId(code);
                    nodeDef.setType(findJavaExecutor(code) != null ? "java" : "agent");
                    nodeDef.setRef(code);
                    nodeDef.setRequireApproval(false);
                    nodeDef.setTimeoutSeconds(120);
                    nodes.add(nodeDef);
                    nodeCodes.add(code);
                } else if (item instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) item;
                    String ref = (String) map.get("ref");
                    if (ref == null || ref.trim().isEmpty()) {
                        continue;
                    }
                    String type = (String) map.get("type");
                    if (type == null || type.trim().isEmpty()) {
                        type = findJavaExecutor(ref) != null ? "java" : "agent";
                    }
                    boolean requireApproval = false;
                    if (map.get("requireApproval") != null) {
                        requireApproval = (Boolean) map.get("requireApproval");
                    }
                    int timeoutSeconds = 120;
                    if (map.get("timeoutSeconds") != null) {
                        timeoutSeconds = ((Number) map.get("timeoutSeconds")).intValue();
                    }

                    GraphTopology.NodeDef nodeDef = new GraphTopology.NodeDef();
                    nodeDef.setId(ref);
                    nodeDef.setType(type);
                    nodeDef.setRef(ref);
                    nodeDef.setRequireApproval(requireApproval);
                    nodeDef.setTimeoutSeconds(timeoutSeconds);
                    nodes.add(nodeDef);
                    nodeCodes.add(ref);
                }
            }

            if (!nodeCodes.isEmpty()) {
                GraphTopology.EdgeDef startEdge = new GraphTopology.EdgeDef();
                startEdge.setFrom("__start__");
                startEdge.setTo(nodeCodes.get(0));
                edges.add(startEdge);

                for (int i = 0; i < nodeCodes.size() - 1; i++) {
                    GraphTopology.EdgeDef edge = new GraphTopology.EdgeDef();
                    edge.setFrom(nodeCodes.get(i));
                    edge.setTo(nodeCodes.get(i + 1));
                    edges.add(edge);
                }

                GraphTopology.EdgeDef endEdge = new GraphTopology.EdgeDef();
                endEdge.setFrom(nodeCodes.get(nodeCodes.size() - 1));
                endEdge.setTo("__end__");
                edges.add(endEdge);
            }

            topology.setNodes(nodes);
            topology.setEdges(edges);
            return objectMapper.writeValueAsString(topology);

        } catch (Exception e) {
            log.error("旧 nodes 转换为 graphJson 失败: {}", nodesJson, e);
            throw new RuntimeException("nodes 格式转换失败", e);
        }
    }


    private WorkflowNodeExecutor findJavaExecutor(String nodeCode) {
        if (javaExecutors == null || javaExecutors.isEmpty()) {
            return null;
        }
        for (WorkflowNodeExecutor exec : javaExecutors) {
            if (nodeCode.equals(exec.getNodeCode())) {
                return exec;
            }
        }
        return null;
    }

    private AiTool findAiTool(String simpleClassName) {
        if (allTools == null || allTools.isEmpty()) {
            return null;
        }
        for (AiTool tool : allTools) {
            if (simpleClassName.equals(AopUtils.getTargetClass(tool).getSimpleName())) {
                return tool;
            }
        }
        return null;
    }

    public String buildMermaid(GraphTopology topology) {
        StringBuilder sb = new StringBuilder("graph TD\n");

        for (GraphTopology.NodeDef node : topology.getNodes()) {
            String nodeId = cleanForMermaid(node.getId());
            String shape;
            if ("classifier".equals(node.getType())) {
                shape = "{" + nodeId + "}";        // 菱形 = 分类决策节点
            } else if ("agent".equals(node.getType())) {
                shape = "([" + nodeId + "])";      // 圆角 = 智能体
            } else {
                shape = "[" + nodeId + "]";        // 方形 = Java 节点
            }
            sb.append("  ").append(nodeId).append(shape).append("\n");
        }

        for (GraphTopology.EdgeDef edge : topology.getEdges()) {
            String from = "__start__".equals(edge.getFrom()) ? "START((Start))" : cleanForMermaid(edge.getFrom());
            String to = "__end__".equals(edge.getTo()) ? "END((End))" : cleanForMermaid(edge.getTo());
            String cond = cleanCondition(edge.getCondition());

            if (!cond.isEmpty()) {
                sb.append("  ").append(from).append(" -->|").append(cond).append("| ").append(to).append("\n");
            } else {
                sb.append("  ").append(from).append(" --> ").append(to).append("\n");
            }
        }

        return sb.toString();
    }

    private String cleanForMermaid(String val) {
        if (val == null) return "";
        return val.replaceAll("[^a-zA-Z0-9_.-]", "");
    }

    private String cleanCondition(String cond) {
        if (cond == null) return "";
        return cond.replaceAll("[;\"'()|\\{\\}\\[\\]<>#`+*]", "").trim();
    }

    /**
     * 直接查询数据库获取最后一条 checkpoint 的 __next_node_id__
     * 这是判断图是否完成的唯一可靠来源（getState().next() 返回图的就绪位置，不可靠）
     */
    @SuppressWarnings("unchecked")
    private String queryLastNextNodeId(String executionId) {
        try {
            String sql = "SELECT state_json FROM ai_graph_checkpoint WHERE execution_id = ? " +
                    "ORDER BY sequence_no DESC LIMIT 1";
            String stateJson = jdbcTemplate.queryForObject(sql, String.class, executionId);
            if (stateJson != null) {
                Map<String, Object> state = objectMapper.readValue(stateJson, Map.class);
                return (String) state.get("__next_node_id__");
            }
        } catch (Exception e) {
            log.warn(">>> [LangGraph4j] 查询最后 checkpoint 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 将当前工作流的最新输出保存为助理聊天记录入库
     */
    @SuppressWarnings("unchecked")
    private void saveWorkflowAssistantMessage(String executionId, Long conversationId) {
        if (conversationId == null) {
            return;
        }
        try {
            String sql = "SELECT state_json FROM ai_graph_checkpoint WHERE execution_id = ? " +
                    "ORDER BY sequence_no DESC LIMIT 1";
            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, executionId);
            if (list == null || list.isEmpty()) {
                log.warn(">>> [LangGraph4j] 未找到 executionId={} 的检查点，无法保存 AI 消息", executionId);
                return;
            }
            String stateJson = (String) list.get(0).get("state_json");
            if (stateJson == null || stateJson.trim().isEmpty()) {
                return;
            }
            
            // 解析 JSON 获取 latestOutput
            Map<String, Object> stateMap = objectMapper.readValue(stateJson, Map.class);
            String latestOutput = (String) stateMap.get(PolarisAgentState.LATEST_OUTPUT);
            if (latestOutput == null) {
                latestOutput = "";
            }

            // 优化防护：如果最新输出以 "{" 开头且以 "}" 结尾，或者目前正在挂起等待审批中，则不要把中间路由的 JSON 输出写为助理回复
            // 此时，友好地展现一条状态文字：“工作流已挂起，正在等待人工审核确认。”
            if ((latestOutput.trim().startsWith("{") && latestOutput.trim().endsWith("}")) || latestOutput.trim().isEmpty()) {
                List<Map<String, Object>> threadInfo = jdbcTemplate.queryForList(
                        "SELECT status FROM ai_graph_checkpoint WHERE execution_id = ? " +
                                "ORDER BY sequence_no DESC LIMIT 1",
                        executionId
                );
                if (threadInfo != null && !threadInfo.isEmpty() && "paused".equals(threadInfo.get(0).get("status"))) {
                    latestOutput = "工作流已挂起，正在等待人工审核确认。";
                } else if (latestOutput.trim().startsWith("{")) {
                    latestOutput = ""; // 如果是正常的流式过程中的 JSON，置空以不影响后续展示
                }
            }

            int updated = jdbcTemplate.update(
                    "UPDATE ai_message SET content = ? " +
                            "WHERE workflow_execution_id = ? AND role = 'assistant'",
                    latestOutput, executionId);
            if (updated > 0) {
                return;
            }

            // 往 ai_message 插入一条 assistant 消息
            com.polaris.ai.domain.AiMessage aiMsg = new com.polaris.ai.domain.AiMessage();
            aiMsg.setConversationId(conversationId);
            aiMsg.setWorkflowExecutionId(executionId);
            aiMsg.setRole("assistant");
            aiMsg.setContent(latestOutput);
            aiChatMapper.insertMessage(aiMsg);
            
            log.info(">>> [LangGraph4j] 成功向会话 {} 插入新 AI 消息，内容长度: {}", conversationId, latestOutput.length());
        } catch (Exception e) {
            log.error(">>> [LangGraph4j] 保存 AI 消息失败", e);
        }
    }
}
