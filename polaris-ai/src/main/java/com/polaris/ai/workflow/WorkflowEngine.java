package com.polaris.ai.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.chat.AiAssistant;
import com.polaris.ai.domain.AiAgent;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.domain.AiWorkflow;
import com.polaris.ai.pivot.AiModelFactory;
import com.polaris.ai.service.IAiAgentService;
import com.polaris.ai.service.IAiModelConfigService;
import com.polaris.ai.service.IAiWorkflowService;
import com.polaris.ai.tools.base.AiTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * 智能体工作流执行引擎
 * 负责解析工作流拓扑结构、动态装配智能体、跨线程传递上下文、以及按序将各 Agent 的流式输出推送至 SSE
 *
 * @author polaris
 */
@Slf4j
@Service
public class WorkflowEngine {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private IAiWorkflowService workflowService;

    @Autowired
    private IAiAgentService agentService;

    @Autowired
    private IAiModelConfigService modelConfigService;

    @Autowired
    private AiModelFactory modelFactory;

    @Autowired(required = false)
    private List<AiTool> allTools = new ArrayList<>();

    @Autowired(required = false)
    private List<WorkflowNodeExecutor> javaExecutors = new ArrayList<>();

    /**
     * 运行指定的工作流
     *
     * @param workflowCode    工作流唯一编码
     * @param userInput       用户原始提问
     * @param securityContext 线程安全上下文
     * @param emitter         SSE 发射器
     */
    public void run(String workflowCode, String userInput, SecurityContext securityContext, SseEmitter emitter) {
        log.info(">>> 启动智能体工作流，代码: {}, 输入: {}", workflowCode, userInput);
        WorkflowContext context = new WorkflowContext(userInput, emitter);

        try {
            // 1. 获取工作流配置
            AiWorkflow workflow = workflowService.selectWorkflowByCode(workflowCode);
            if (workflow == null) {
                sendSse(emitter, "error", "找不到对应且启用的工作流，编码: " + workflowCode);
                return;
            }

            // 2. 解析节点列表 JSON
            List<String> nodeCodes = parseNodesJson(workflow.getNodes());
            if (nodeCodes == null || nodeCodes.isEmpty()) {
                sendSse(emitter, "error", "工作流未编排任何节点，无法执行");
                return;
            }

            // 3. 逐个节点顺序执行
            for (int i = 0; i < nodeCodes.size(); i++) {
                String nodeCode = nodeCodes.get(i);
                
                // 3.1 尝试寻找纯 Java 业务执行节点
                WorkflowNodeExecutor javaExec = findJavaExecutor(nodeCode);
                if (javaExec != null) {
                    log.info(">>> 执行 Java 业务节点: {}", nodeCode);
                    sendSse(emitter, "node_start", nodeCode + "|[系统任务]");
                    try {
                        javaExec.execute(context);
                        sendSse(emitter, "node_done", nodeCode);
                    } catch (Exception e) {
                        log.error("Java 节点 [{}] 执行异常", nodeCode, e);
                        sendSse(emitter, "node_error", nodeCode + "|" + e.getMessage());
                        throw e; // 中断工作流
                    }
                    continue;
                }

                // 3.2 尝试寻找 AI 智能体节点
                AiAgent agent = agentService.selectAgentByCode(nodeCode);
                if (agent != null) {
                    log.info(">>> 执行 AI 智能体节点: {} ({})", nodeCode, agent.getAgentName());
                    executeAgentNode(agent, context, securityContext);
                    continue;
                }

                log.warn(">>> 发现未注册或未启用的节点编码: {}，自动跳过", nodeCode);
            }

            // 4. 工作流全部节点执行完毕
            sendSse(emitter, "workflow_done", "[DONE]");
            emitter.complete();
            log.info(">>> 智能体工作流执行完成，代码: {}", workflowCode);

        } catch (Exception e) {
            log.error("工作流执行中断", e);
            sendSse(emitter, "error", "工作流异常中断: " + e.getMessage());
            emitter.complete();
        }
    }

    /**
     * 执行智能体节点（拉起 LLM 与工具进行交互，并将流式输出输出至客户端）
     */
    private void executeAgentNode(AiAgent agent, WorkflowContext context, SecurityContext securityContext) throws Exception {
        SseEmitter emitter = context.getEmitter();
        String nodeCode = agent.getAgentCode();

        // 1. 发送节点启动消息
        sendSse(emitter, "node_start", nodeCode + "|" + agent.getAgentName());

        // 2. 动态加载该智能体配置的底座模型
        StreamingChatModel chatModel = modelFactory.getStreamingModel(agent.getModelName());

        // 3. 根据智能体绑定的工具清单，过滤并反射装载工具 Bean，传递线程上下文
        String searchKey = null;
        try {
            AiModelConfig modelConfig = modelConfigService.selectModelConfigByModelName(agent.getModelName());
            if (modelConfig != null) {
                searchKey = modelConfig.getSearchKey();
            }
        } catch (Exception e) {
            log.warn(">>> 读取模型联网搜索配置失败: {}", e.getMessage());
        }
        Map<ToolSpecification, ToolExecutor> activeTools = getActiveTools(agent.getTools(), securityContext, searchKey, emitter, nodeCode);

        // 4. 动态声明式 AI 服务代理装配
        AiAssistant assistant = AiServices.builder(AiAssistant.class)
                .streamingChatModel(chatModel)
                .systemMessageProvider(chatCtx -> agent.getSystemPrompt())
                .tools(activeTools)
                .build();

        // 5. 准备对话的输入上下文（如果是第一个节点，用用户原始输入；如果是后续节点，用上个智能体的最新回复）
        List<ChatMessage> history = new ArrayList<>();
        String inputPrompt = context.getLatestOutput() != null ? context.getLatestOutput() : context.getUserInput();
        history.add(UserMessage.from(inputPrompt));

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder fullReply = new StringBuilder();
        
        // 6. 发起流式请求
        TokenStream tokenStream = assistant.chat(history);
        tokenStream.onPartialResponse(token -> {
                    fullReply.append(token);
                    sendSse(emitter, "node_chunk", nodeCode + "|" + token.replace("\n", "__SSE_NEWLINE__"));
                })
                .onPartialThinking(thinking -> {
                    if (thinking != null && thinking.text() != null) {
                        sendSse(emitter, "node_thinking", nodeCode + "|" + thinking.text().replace("\n", "__SSE_NEWLINE__"));
                    }
                })
                .onCompleteResponse(response -> {
                    context.setLatestOutput(fullReply.toString());
                    sendSse(emitter, "node_done", nodeCode);
                    latch.countDown();
                })
                .onError(error -> {
                    log.error("智能体节点 [{}] 运行流式报错", nodeCode, error);
                    sendSse(emitter, "node_error", nodeCode + "|" + error.getMessage());
                    latch.countDown();
                })
                .start();

        // 挂起当前执行线程，等待 AI 流式输出完毕（确保多节点串行执行）
        latch.await();
    }

    /**
     * 根据英文逗号分隔的 Tools 类名，过滤系统中加载的 Spring Bean 工具，并包装为上下文安全执行器
     */
    private Map<ToolSpecification, ToolExecutor> getActiveTools(String toolsConfig, SecurityContext securityContext, String searchKey, SseEmitter emitter, String nodeCode) {
        Map<ToolSpecification, ToolExecutor> map = new HashMap<>();
        if (toolsConfig == null || toolsConfig.trim().isEmpty() || allTools == null || allTools.isEmpty()) {
            return map;
        }

        // 解析智能体绑定的工具集合
        Set<String> toolNames = new HashSet<>();
        for (String t : toolsConfig.split(",")) {
            toolNames.add(t.trim());
        }

        // 构造当前线程 Web 请求上下文拷贝，防异步回收
        SimpleRequestAttributes simpleAttrs = new SimpleRequestAttributes(RequestContextHolder.getRequestAttributes());

        for (Object toolObj : allTools) {
            Class<?> targetClass = AopUtils.getTargetClass(toolObj);
            String className = targetClass.getSimpleName();
            
            // 匹配类名
            if (toolNames.contains(className)) {
                java.lang.reflect.Method[] methods = targetClass.getDeclaredMethods();
                for (java.lang.reflect.Method method : methods) {
                    if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                        ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
                        ToolExecutor originalExecutor = new DefaultToolExecutor(toolObj, method);
                        ToolExecutor wrappedExecutor = new SecurityContextPropagatingToolExecutor(
                                originalExecutor, securityContext, simpleAttrs, searchKey, emitter, nodeCode);
                        map.put(spec, wrappedExecutor);
                    }
                }
            }
        }
        return map;
    }

    private List<String> parseNodesJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            log.error("解析工作流节点编排 JSON 异常: {}", json, e);
            return null;
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

    private void sendSse(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            log.warn("工作流 SSE 推送失败: event={}", event);
        }
    }

    // ================================================================
    //  内部辅助线程安全上下文封装类（安全重用已有的装饰器模式）
    // ================================================================

    private class SecurityContextPropagatingToolExecutor implements ToolExecutor {
        private final ToolExecutor delegate;
        private final SecurityContext securityContext;
        private final RequestAttributes requestAttributes;
        private final String searchKey;
        private final SseEmitter emitter;
        private final String nodeCode;

        public SecurityContextPropagatingToolExecutor(ToolExecutor delegate, SecurityContext securityContext,
                                                     RequestAttributes requestAttributes, String searchKey,
                                                     SseEmitter emitter, String nodeCode) {
            this.delegate = delegate;
            this.securityContext = securityContext;
            this.requestAttributes = requestAttributes;
            this.searchKey = searchKey;
            this.emitter = emitter;
            this.nodeCode = nodeCode;
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            SecurityContext previousContext = SecurityContextHolder.getContext();
            RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
            try {
                // 执行工具前向前端推送 node_tool 状态事件
                sendSse(emitter, "node_tool", nodeCode + "|" + request.name());
                
                SecurityContextHolder.setContext(securityContext);
                if (requestAttributes != null) {
                    RequestContextHolder.setRequestAttributes(requestAttributes);
                }
                com.polaris.ai.utils.SearchKeyHolder.set(searchKey);
                return delegate.execute(request, memoryId);
            } finally {
                com.polaris.ai.utils.SearchKeyHolder.clear();
                RequestContextHolder.resetRequestAttributes();
                if (previousAttributes != null) {
                    RequestContextHolder.setRequestAttributes(previousAttributes);
                }
                if (previousContext != null) {
                    SecurityContextHolder.setContext(previousContext);
                } else {
                    SecurityContextHolder.clearContext();
                }
            }
        }
    }

    private static class SimpleRequestAttributes implements RequestAttributes {
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        public SimpleRequestAttributes(RequestAttributes originalAttrs) {
            if (originalAttrs != null) {
                try {
                    for (String name : originalAttrs.getAttributeNames(SCOPE_REQUEST)) {
                        Object val = originalAttrs.getAttribute(name, SCOPE_REQUEST);
                        if (val != null) {
                            attributes.put(name, val);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        @Override
        public Object getAttribute(String name, int scope) {
            return scope == SCOPE_REQUEST ? attributes.get(name) : null;
        }

        @Override
        public void setAttribute(String name, Object value, int scope) {
            if (scope == SCOPE_REQUEST) {
                if (value != null) {
                    attributes.put(name, value);
                } else {
                    attributes.remove(name);
                }
            }
        }

        @Override
        public void removeAttribute(String name, int scope) {
            if (scope == SCOPE_REQUEST) {
                attributes.remove(name);
            }
        }

        @Override
        public String[] getAttributeNames(int scope) {
            return scope == SCOPE_REQUEST ? attributes.keySet().toArray(new String[0]) : new String[0];
        }

        @Override
        public void registerDestructionCallback(String name, Runnable callback, int scope) {
        }

        @Override
        public Object resolveReference(String key) {
            return null;
        }

        @Override
        public String getSessionId() {
            return "session";
        }

        @Override
        public Object getSessionMutex() {
            return this;
        }
    }
}
