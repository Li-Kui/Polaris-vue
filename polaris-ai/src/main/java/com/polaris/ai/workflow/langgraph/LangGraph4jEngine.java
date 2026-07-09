package com.polaris.ai.workflow.langgraph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.chat.AiAssistant;
import com.polaris.ai.domain.AiAgent;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.domain.AiWorkflow;
import com.polaris.ai.helper.SsePushHelper;
import com.polaris.ai.pivot.AiModelFactory;
import com.polaris.ai.service.IAiAgentService;
import com.polaris.ai.service.IAiModelConfigService;
import com.polaris.ai.service.IAiWorkflowService;
import com.polaris.ai.tools.SecurityContextToolExecutor;
import com.polaris.ai.tools.base.AiTool;
import com.polaris.ai.workflow.WorkflowNodeExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    private final Map<String, GraphTopology> topologyCache = new ConcurrentHashMap<>();

    @Autowired
    private SpringJdbcCheckpointSaver springJdbcCheckpointSaver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IAiWorkflowService workflowService;

    @Autowired
    private IAiAgentService agentService;

    @Autowired
    private IAiModelConfigService modelConfigService;

    @Autowired
    private AiModelFactory modelFactory;

    @Autowired
    private SsePushHelper sseHelper;

    @Autowired
    private com.polaris.ai.mapper.AiChatMapper aiChatMapper;

    @Autowired(required = false)
    private List<AiTool> allTools = new ArrayList<>();

    @Autowired(required = false)
    private List<WorkflowNodeExecutor> javaExecutors = new ArrayList<>();

    /**
     * 系统启动时自动检测并初始化建表
     */
    @PostConstruct
    public void init() {
        springJdbcCheckpointSaver.setup();
    }

    /**
     * 运行图工作流（初次运行）
     */
    public void run(String workflowCode, String userInput, String threadId,
                    SecurityContext securityContext, SseEmitter emitter, Long conversationId, Long userId) {
        log.info(">>> [LangGraph4j] 启动图工作流，代码: {}, 输入: {}", workflowCode, userInput);

        String finalThreadId = (threadId != null && !threadId.isEmpty()) ? threadId : UUID.randomUUID().toString();

        // 1. 如果有关联会话，保存用户发送的消息并自动命名会话
        if (conversationId != null) {
            try {
                com.polaris.ai.domain.AiMessage userMsg = new com.polaris.ai.domain.AiMessage();
                userMsg.setConversationId(conversationId);
                userMsg.setRole("user");
                userMsg.setContent(userInput);
                aiChatMapper.insertMessage(userMsg);

                // 首条消息自动命名会话标题
                com.polaris.ai.domain.AiConversation conv = aiChatMapper.selectConversationById(conversationId, userId);
                if (conv != null && "新对话".equals(conv.getTitle())) {
                    String autoTitle = userInput.length() > 15 ? userInput.substring(0, 15) + "…" : userInput;
                    aiChatMapper.updateConversationTitle(conversationId, autoTitle, userId);
                }
            } catch (Exception e) {
                log.error(">>> [LangGraph4j] 保存用户消息或自动命名会话失败: {}", e.getMessage());
            }
        }

        try {
            // 2. 获取工作流配置
            AiWorkflow workflow = workflowService.selectWorkflowByCode(workflowCode);
            if (workflow == null) {
                sseHelper.sendSse(emitter, "error", "找不到对应且启用的工作流，编码: " + workflowCode);
                return;
            }

            // 3. 降级转换 graphJson
            String graphJson = workflow.getGraphJson();
            if (graphJson == null || graphJson.trim().isEmpty()) {
                if (workflow.getNodes() != null && !workflow.getNodes().trim().isEmpty()) {
                    log.warn(">>> 工作流 {} 尚未迁移 graphJson，实时转换中", workflowCode);
                    graphJson = convertNodesToGraphJson(workflow.getNodes());
                } else {
                    sseHelper.sendSse(emitter, "error", "工作流未配置任何节点");
                    return;
                }
            }

            // 4. 解析与校验
            GraphTopology topology = getOrParseTopology(workflowCode, graphJson);
            topology.validate();

            // 5. 构建 StateGraph
            StateGraph<PolarisAgentState> stateGraph = buildStateGraph(topology, securityContext, emitter);

            // 6. 解析审批中断节点并编译图
            List<String> interruptNodes = topology.getNodes().stream()
                    .filter(GraphTopology.NodeDef::isRequireApproval)
                    .map(GraphTopology.NodeDef::getId)
                    .collect(Collectors.toList());

            CompileConfig.Builder compileConfigBuilder = CompileConfig.builder()
                    .checkpointSaver(springJdbcCheckpointSaver);

            if (!interruptNodes.isEmpty()) {
                compileConfigBuilder.interruptBefore(interruptNodes.toArray(new String[0]));
                log.info(">>> [LangGraph4j] 注册审批中断节点: {}", interruptNodes);
            }

            var compiledGraph = stateGraph.compile(compileConfigBuilder.build());

            // 7. 构建初始状态
            Map<String, Object> initialState = new HashMap<>();
            initialState.put(PolarisAgentState.USER_INPUT, userInput);
            initialState.put(PolarisAgentState.LATEST_OUTPUT, "");
            initialState.put(PolarisAgentState.ROUTE_DECISION, "");
            initialState.put(PolarisAgentState.VARIABLES, new HashMap<>());
            initialState.put(PolarisAgentState.ITERATION_COUNT, 0);

            RunnableConfig runConfig = RunnableConfig.builder()
                    .threadId(finalThreadId)
                    .putMetadata("workflow_code", workflowCode)
                    .build();

            // 8. 执行图
            log.info(">>> [LangGraph4j] 开始执行 compiledGraph.invoke()...");
            compiledGraph.invoke(initialState, runConfig);
            log.info(">>> [LangGraph4j] compiledGraph.invoke() 已返回");

            // 判断是否完成：直接查数据库最后一条 checkpoint 的 __next_node_id__
            // （getState().next() 返回的是图性就绪位置，不可靠）
            String lastNextNodeId = queryLastNextNodeId(finalThreadId);
            log.info(">>> [LangGraph4j] 数据库最后 checkpoint 的 __next_node_id__ = '{}'", lastNextNodeId);

            if (lastNextNodeId != null && !"__END__".equalsIgnoreCase(lastNextNodeId) && !"__end__".equalsIgnoreCase(lastNextNodeId)) {
                // 图在某个节点前中断挂起（HITL 审批场景）
                log.info(">>> [LangGraph4j] 工作流在节点 {} 前挂起等待审批，threadId: {}", lastNextNodeId, finalThreadId);
                jdbcTemplate.update("UPDATE ai_graph_checkpoint SET status = 'paused' WHERE thread_id = ? ORDER BY create_time DESC LIMIT 1", finalThreadId);
                sseHelper.sendSse(emitter, "node_interrupt", lastNextNodeId + "|等待人工审核确认");
            } else {
                // 图正常完成
                log.info(">>> [LangGraph4j] 工作流正常完成，threadId: {}", finalThreadId);
                jdbcTemplate.update("UPDATE ai_graph_checkpoint SET status = 'done' WHERE thread_id = ? ORDER BY create_time DESC LIMIT 1", finalThreadId);
                sseHelper.sendSse(emitter, "workflow_done", "[DONE]");
            }
            saveWorkflowAssistantMessage(finalThreadId, conversationId);
            emitter.complete();

        } catch (Exception e) {
            handleException(e, finalThreadId, emitter);
        }
    }

    /**
     * 恢复图工作流（审批通过或出错重试）
     */
    public void resume(String workflowCode, String threadId, Map<String, Object> resumeInput,
                       SecurityContext securityContext, SseEmitter emitter, Long conversationId, Long userId) {
        log.info(">>> [LangGraph4j] 恢复图工作流执行，threadId: {}", threadId);

        try {
            AiWorkflow workflow = workflowService.selectWorkflowByCode(workflowCode);
            if (workflow == null) {
                sseHelper.sendSse(emitter, "error", "找不到对应工作流");
                return;
            }

            String graphJson = workflow.getGraphJson();
            if (graphJson == null || graphJson.trim().isEmpty()) {
                if (workflow.getNodes() != null) {
                    graphJson = convertNodesToGraphJson(workflow.getNodes());
                } else {
                    sseHelper.sendSse(emitter, "error", "工作流未编排");
                    return;
                }
            }

            GraphTopology topology = getOrParseTopology(workflowCode, graphJson);
            StateGraph<PolarisAgentState> stateGraph = buildStateGraph(topology, securityContext, emitter);

            // 编译配置
            List<String> interruptNodes = topology.getNodes().stream()
                    .filter(GraphTopology.NodeDef::isRequireApproval)
                    .map(GraphTopology.NodeDef::getId)
                    .collect(Collectors.toList());

            CompileConfig.Builder compileConfigBuilder = CompileConfig.builder()
                    .checkpointSaver(springJdbcCheckpointSaver);

            if (!interruptNodes.isEmpty()) {
                compileConfigBuilder.interruptBefore(interruptNodes.toArray(new String[0]));
            }

            var compiledGraph = stateGraph.compile(compileConfigBuilder.build());

            RunnableConfig runConfig = RunnableConfig.builder()
                    .threadId(threadId)
                    .putMetadata("workflow_code", workflowCode)
                    .build();

            // 1. 继承上个版本的 iterationCount 和 variables 并写入更新的属性（防多态序列化崩溃，variables 内容需为基础类型）
            Optional<Checkpoint> lastCheckpoint = springJdbcCheckpointSaver.get(runConfig);
            if (lastCheckpoint.isEmpty()) {
                sseHelper.sendSse(emitter, "error", "找不到历史检查点，无法恢复");
                return;
            }

            Map<String, Object> lastState = lastCheckpoint.get().getState();
            int currentIteration = ((Number) lastState.getOrDefault(PolarisAgentState.ITERATION_COUNT, 0)).intValue();

            // 构造状态更新 map
            Map<String, Object> stateUpdate = new HashMap<>();
            stateUpdate.put(PolarisAgentState.ITERATION_COUNT, currentIteration);
            
            // 写入审批做出的路由决策值或者用户反馈信息
            if (resumeInput.get("approve") != null) {
                boolean approve = (Boolean) resumeInput.get("approve");
                stateUpdate.put(PolarisAgentState.ROUTE_DECISION, approve ? "approved" : "rejected");
            }
            if (resumeInput.get("feedback") != null) {
                stateUpdate.put(PolarisAgentState.LATEST_OUTPUT, resumeInput.get("feedback"));
            }

            // 更新当前检查点状态，并获取返回的带最新 checkpointId 属性的 nextConfig
            RunnableConfig nextConfig = compiledGraph.updateState(runConfig, stateUpdate);

            // 2. 物理恢复执行：传入强转后的 null 并使用 nextConfig 驱动 invoke
            log.info(">>> [LangGraph4j] resume: 开始执行 compiledGraph.invoke(null)...");
            compiledGraph.invoke((Map<String, Object>) null, nextConfig);
            log.info(">>> [LangGraph4j] resume: compiledGraph.invoke(null) 已返回");

            // 3. 查数据库最后 checkpoint 的 __next_node_id__ 判断是否完成
            String lastNextNodeId = queryLastNextNodeId(threadId);
            log.info(">>> [LangGraph4j] resume: 数据库最后 checkpoint __next_node_id__ = '{}'", lastNextNodeId);

            if (lastNextNodeId != null && !"__END__".equalsIgnoreCase(lastNextNodeId) && !"__end__".equalsIgnoreCase(lastNextNodeId)) {
                log.info(">>> [LangGraph4j] 恢复后工作流在节点 {} 再次挂起", lastNextNodeId);
                jdbcTemplate.update("UPDATE ai_graph_checkpoint SET status = 'paused' WHERE thread_id = ? ORDER BY create_time DESC LIMIT 1", threadId);
                sseHelper.sendSse(emitter, "node_interrupt", lastNextNodeId + "|等待下阶段审核");
            } else {
                log.info(">>> [LangGraph4j] 恢复后工作流正常完成，threadId: {}", threadId);
                jdbcTemplate.update("UPDATE ai_graph_checkpoint SET status = 'done' WHERE thread_id = ? ORDER BY create_time DESC LIMIT 1", threadId);
                sseHelper.sendSse(emitter, "workflow_done", "[DONE]");
            }
            saveWorkflowAssistantMessage(threadId, conversationId);
            emitter.complete();

        } catch (Exception e) {
            handleException(e, threadId, emitter);
        }
    }

    /**
     * 统一的图异常处理器（检测 SSE 断开和置脏状态）
     */
    private void handleException(Exception e, String threadId, SseEmitter emitter) {
        log.error(">>> [LangGraph4j] 工作流运行中发生异常，threadId: {}", threadId, e);

        // 如果是由于客户端连接主动断开退出引起的异常
        if (e.getMessage() != null && e.getMessage().contains("SSE_CONNECTION_LOST")) {
            log.warn(">>> [LangGraph4j] 客户端连接关闭引起的中断。状态保持 paused 不置 error，允许刷新页面重试。");
            jdbcTemplate.update("UPDATE ai_graph_checkpoint SET status = 'paused' WHERE thread_id = ? ORDER BY create_time DESC LIMIT 1", threadId);
        } else {
            jdbcTemplate.update("UPDATE ai_graph_checkpoint SET status = 'error' WHERE thread_id = ? ORDER BY create_time DESC LIMIT 1", threadId);
            sseHelper.sendSse(emitter, "error", "工作流执行中断: " + e.getMessage());
        }
        emitter.complete();
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

    // ================================================================
    //  私有方法：图构建
    // ================================================================

    /**
     * 构建 StateGraph，注册所有节点和边
     */
    private StateGraph<PolarisAgentState> buildStateGraph(
            GraphTopology topology, SecurityContext securityContext, SseEmitter emitter) throws Exception {

        StateGraph<PolarisAgentState> graph = new StateGraph<>(PolarisAgentState.SCHEMA, PolarisAgentState::new);
        int maxIterations = topology.getMaxIterations();

        // 注册节点
        for (GraphTopology.NodeDef nodeDef : topology.getNodes()) {
            NodeAction<PolarisAgentState> action = createNodeAction(nodeDef, securityContext, emitter);
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

                String fromNode = "__start__".equals(from) ? START : from;
                graph.addConditionalEdges(fromNode,
                        edge_async(state -> {
                            // 循环保护
                            if (state.iterationCount() >= maxIterations) {
                                log.warn(">>> [LangGraph4j] 达到最大循环次数 {}，强制终止", maxIterations);
                                return "__default__";
                            }
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
            GraphTopology.NodeDef nodeDef, SecurityContext securityContext, SseEmitter emitter) {

        String nodeType = nodeDef.getType();
        String ref = nodeDef.getRef();
        int timeoutSeconds = nodeDef.getTimeoutSeconds();

        if ("agent".equals(nodeType)) {
            return createAgentNodeAction(ref, timeoutSeconds, securityContext, emitter);
        } else if ("java".equals(nodeType)) {
            WorkflowNodeExecutor executor = findJavaExecutor(ref);
            if (executor == null) {
                throw new IllegalArgumentException("找不到 Java 节点执行器: " + ref);
            }
            return createJavaNodeAction(executor, emitter);
        } else {
            throw new IllegalArgumentException("未知的节点类型: " + nodeType);
        }
    }

    /**
     * 创建 Agent 节点的 NodeAction
     */
    private NodeAction<PolarisAgentState> createAgentNodeAction(
            String agentCode, int timeoutSeconds, SecurityContext securityContext, SseEmitter emitter) {

        return state -> {
            AiAgent agent = agentService.selectAgentByCode(agentCode);
            if (agent == null) {
                throw new RuntimeException("找不到智能体: " + agentCode);
            }

            String nodeCode = agent.getAgentCode();
            log.info(">>> [LangGraph4j] 执行 AI 智能体节点: {} ({})", nodeCode, agent.getAgentName());

            sseHelper.sendSse(emitter, "node_start", nodeCode + "|" + agent.getAgentName());

            SecurityContext previousContext = SecurityContextHolder.getContext();
            try {
                SecurityContextHolder.setContext(securityContext);

                StreamingChatModel chatModel = modelFactory.getStreamingModel(agent.getModelName());

                String searchKey = null;
                try {
                    AiModelConfig modelConfig = modelConfigService.selectModelConfigByModelName(agent.getModelName());
                    if (modelConfig != null) {
                        searchKey = modelConfig.getSearchKey();
                    }
                } catch (Exception e) {
                    log.warn(">>> 读取模型联网搜索配置失败: {}", e.getMessage());
                }

                Map<ToolSpecification, ToolExecutor> activeTools =
                        SecurityContextToolExecutor.getFilteredTools(
                                allTools, agent.getTools(), securityContext, searchKey, emitter, nodeCode);

                AiAssistant assistant = AiServices.builder(AiAssistant.class)
                        .streamingChatModel(chatModel)
                        .systemMessageProvider(ctx -> agent.getSystemPrompt())
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
                final Exception[] streamError = {null};

                TokenStream tokenStream = assistant.chat(history);
                tokenStream.onPartialResponse(token -> {
                            fullReply.append(token);
                            try {
                                sseHelper.sendSse(emitter, "node_chunk",
                                        nodeCode + "|" + token.replace("\n", "__SSE_NEWLINE__"));
                            } catch (Exception e) {
                                // 捕获并记录客户端主动断开
                                streamError[0] = new RuntimeException("SSE_CONNECTION_LOST", e);
                                latch.countDown();
                            }
                        })
                        .onPartialThinking(thinking -> {
                            if (thinking != null && thinking.text() != null) {
                                try {
                                    sseHelper.sendSse(emitter, "node_thinking",
                                            nodeCode + "|" + thinking.text().replace("\n", "__SSE_NEWLINE__"));
                                } catch (Exception ignored) {
                                }
                            }
                        })
                        .onCompleteResponse(response -> {
                            try {
                                sseHelper.sendSse(emitter, "node_done", nodeCode);
                            } catch (Exception ignored) {
                            }
                            latch.countDown();
                        })
                        .onError(error -> {
                            log.error("[LangGraph4j] 智能体节点 [{}] 流式报错", nodeCode, error);
                            streamError[0] = (Exception) error;
                            try {
                                sseHelper.sendSse(emitter, "node_error", nodeCode + "|" + error.getMessage());
                            } catch (Exception ignored) {
                            }
                            latch.countDown();
                        })
                        .start();

                // 超时与主动断开双防线
                if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                    sseHelper.sendSse(emitter, "node_error", nodeCode + "|节点执行超时(" + timeoutSeconds + "s)");
                    throw new RuntimeException("节点 " + nodeCode + " 执行超时: " + timeoutSeconds + "s");
                }

                if (streamError[0] != null) {
                    throw streamError[0];
                }

                Map<String, Object> updates = new HashMap<>();
                updates.put(PolarisAgentState.LATEST_OUTPUT, fullReply.toString());
                updates.put(PolarisAgentState.ITERATION_COUNT, state.iterationCount() + 1);
                updates.put(PolarisAgentState.ROUTE_DECISION, ""); // 运行完毕后自动重置上一轮路由决策残留，防止污染其它条件路由
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
            WorkflowNodeExecutor executor, SseEmitter emitter) {

        return state -> {
            String nodeCode = executor.getNodeCode();
            sseHelper.sendSse(emitter, "node_start", nodeCode + "|[系统任务]");

            try {
                NodeAction<PolarisAgentState> adapted = WorkflowNodeExecutorAdapter.adapt(executor, emitter);
                Map<String, Object> result = adapted.apply(state);

                sseHelper.sendSse(emitter, "node_done", nodeCode);

                Map<String, Object> updates = new HashMap<>(result);
                updates.put(PolarisAgentState.ITERATION_COUNT, state.iterationCount() + 1);
                updates.put(PolarisAgentState.ROUTE_DECISION, ""); // 运行完毕后自动重置上一轮路由决策残留，防止污染其它条件路由
                return updates;

            } catch (Exception e) {
                log.error("[LangGraph4j] Java 节点 [{}] 执行异常", nodeCode, e);
                sseHelper.sendSse(emitter, "node_error", nodeCode + "|" + e.getMessage());
                throw e;
            }
        };
    }

    // ================================================================
    //  私有工具与数据转换方法
    // ================================================================

    private GraphTopology getOrParseTopology(String workflowCode, String graphJson) {
        String cacheKey = workflowCode + "_" + graphJson.hashCode();
        return topologyCache.computeIfAbsent(cacheKey, k -> {
            try {
                return objectMapper.readValue(graphJson, GraphTopology.class);
            } catch (Exception e) {
                throw new RuntimeException("graphJson 解析失败: " + e.getMessage(), e);
            }
        });
    }

    public void invalidateTopologyCache(String workflowCode) {
        topologyCache.entrySet().removeIf(e -> e.getKey().startsWith(workflowCode + "_"));
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

    private String buildMermaid(GraphTopology topology) {
        StringBuilder sb = new StringBuilder("graph TD\n");

        for (GraphTopology.NodeDef node : topology.getNodes()) {
            String shape = "agent".equals(node.getType()) ? "([" + node.getId() + "])" : "[" + node.getId() + "]";
            sb.append("  ").append(node.getId()).append(shape).append("\n");
        }

        for (GraphTopology.EdgeDef edge : topology.getEdges()) {
            String from = "__start__".equals(edge.getFrom()) ? "START((Start))" : edge.getFrom();
            String to = "__end__".equals(edge.getTo()) ? "END((End))" : edge.getTo();

            if (edge.getCondition() != null && !edge.getCondition().isEmpty()) {
                sb.append("  ").append(from).append(" -->|").append(edge.getCondition()).append("| ").append(to).append("\n");
            } else {
                sb.append("  ").append(from).append(" --> ").append(to).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 直接查询数据库获取最后一条 checkpoint 的 __next_node_id__
     * 这是判断图是否完成的唯一可靠来源（getState().next() 返回图的就绪位置，不可靠）
     */
    @SuppressWarnings("unchecked")
    private String queryLastNextNodeId(String threadId) {
        try {
            String sql = "SELECT state_json FROM ai_graph_checkpoint WHERE thread_id = ? ORDER BY create_time DESC LIMIT 1";
            String stateJson = jdbcTemplate.queryForObject(sql, String.class, threadId);
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
    private void saveWorkflowAssistantMessage(String threadId, Long conversationId) {
        if (conversationId == null) {
            return;
        }
        try {
            String sql = "SELECT state_json FROM ai_graph_checkpoint WHERE thread_id = ? ORDER BY create_time DESC LIMIT 1";
            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, threadId);
            if (list == null || list.isEmpty()) {
                log.warn(">>> [LangGraph4j] 未找到 threadId={} 的检查点，无法保存 AI 消息", threadId);
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
                        "SELECT status FROM ai_graph_checkpoint WHERE thread_id = ? ORDER BY id DESC LIMIT 1",
                        threadId
                );
                if (threadInfo != null && !threadInfo.isEmpty() && "paused".equals(threadInfo.get(0).get("status"))) {
                    latestOutput = "工作流已挂起，正在等待人工审核确认。";
                } else if (latestOutput.trim().startsWith("{")) {
                    latestOutput = ""; // 如果是正常的流式过程中的 JSON，置空以不影响后续展示
                }
            }

            // 检查该会话最后一条消息是否为 assistant 消息。如果是，则更新；否则插入。
            List<Map<String, Object>> lastMessages = jdbcTemplate.queryForList(
                    "SELECT id, role FROM ai_message WHERE conversation_id = ? ORDER BY id DESC LIMIT 1",
                    conversationId
            );
            if (lastMessages != null && !lastMessages.isEmpty()) {
                Map<String, Object> lastMsg = lastMessages.get(0);
                String role = (String) lastMsg.get("role");
                Long msgId = ((Number) lastMsg.get("id")).longValue();
                if ("assistant".equals(role)) {
                    jdbcTemplate.update("UPDATE ai_message SET content = ? WHERE id = ?", latestOutput, msgId);
                    log.info(">>> [LangGraph4j] 成功更新会话 {} 的最新 AI 消息(id={})，内容长度: {}", conversationId, msgId, latestOutput.length());
                    return;
                }
            }

            // 往 ai_message 插入一条 assistant 消息
            com.polaris.ai.domain.AiMessage aiMsg = new com.polaris.ai.domain.AiMessage();
            aiMsg.setConversationId(conversationId);
            aiMsg.setRole("assistant");
            aiMsg.setContent(latestOutput);
            aiChatMapper.insertMessage(aiMsg);
            
            log.info(">>> [LangGraph4j] 成功向会话 {} 插入新 AI 消息，内容长度: {}", conversationId, latestOutput.length());
        } catch (Exception e) {
            log.error(">>> [LangGraph4j] 保存 AI 消息失败", e);
        }
    }
}
