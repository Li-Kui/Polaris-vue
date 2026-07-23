package com.polaris.ai.tools;

import com.polaris.ai.tools.base.AiTool;
import com.polaris.ai.utils.SearchKeyHolder;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一的安全上下文传播工具执行器
 * <p>
 * 抽取自 WorkflowEngine 和 AiToolRegistry 中重复的安全上下文装饰器，
 * 提供跨线程传递 SecurityContext、RequestAttributes 和 SearchKey 的能力。
 *
 * @author polaris
 */
public class SecurityContextToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(SecurityContextToolExecutor.class);

    private static final Map<String, String> TOOL_CLASS_MAP = new HashMap<>();
    static {
        TOOL_CLASS_MAP.put("web_search", "WebSearchTools");
        TOOL_CLASS_MAP.put("image_generate", "ImageGenerateTools");
    }

    /**
     * 将原始 ToolExecutor 包装为具备安全上下文传播能力的 Executor
     */
    public static ToolExecutor wrapExecutor(ToolExecutor originalExecutor, SecurityContext securityContext, String searchKey) {
        SimpleRequestAttributes simpleAttrs = new SimpleRequestAttributes(RequestContextHolder.getRequestAttributes());
        return new PropagatingExecutor(originalExecutor, securityContext, simpleAttrs, searchKey, null, null);
    }

    /**
     * 构建按 Agent 级别过滤的工具集（带安全上下文传播）
     *
     * @param allTools        所有注册的 AI 工具实例
     * @param toolsConfig     Agent 绑定的工具类列表（逗号分隔类名），为 null 则返回空 Map
     * @param securityContext 当前请求的安全上下文
     * @param searchKey       联网搜索 API Key
     * @param emitter         SSE 发射器（用于推送 node_tool 事件）
     * @param nodeCode        当前节点编码（用于 SSE 事件标识）
     * @return 过滤后的工具规格与执行器映射
     */
    public static Map<ToolSpecification, ToolExecutor> getFilteredTools(
            List<? extends AiTool> allTools,
            String toolsConfig,
            SecurityContext securityContext,
            String searchKey,
            SseEmitter emitter,
            String nodeCode) {

        Map<ToolSpecification, ToolExecutor> map = new HashMap<>();
        if (toolsConfig == null || toolsConfig.trim().isEmpty() || allTools == null || allTools.isEmpty()) {
            return map;
        }

        // 解析智能体/工作流节点绑定的工具集合（同时支持类名和快捷别名如 web_search, image_generate）
        Set<String> toolNames = new HashSet<>();
        for (String t : toolsConfig.split(",")) {
            String trimmed = t.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String mappedClass = TOOL_CLASS_MAP.get(trimmed);
            if (mappedClass != null) {
                toolNames.add(mappedClass);
            } else {
                toolNames.add(trimmed);
            }
        }

        SimpleRequestAttributes simpleAttrs = new SimpleRequestAttributes(RequestContextHolder.getRequestAttributes());

        for (Object toolObj : allTools) {
            Class<?> targetClass = AopUtils.getTargetClass(toolObj);
            String className = targetClass.getSimpleName();

            if (toolNames.contains(className)) {
                if (className.contains("WebSearchTools")
                        && (searchKey == null || searchKey.trim().isEmpty())) {
                    continue;
                }
                Method[] methods = targetClass.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                        ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
                        ToolExecutor originalExecutor = new DefaultToolExecutor(toolObj, method);
                        ToolExecutor wrappedExecutor = new PropagatingExecutor(
                                originalExecutor, securityContext, simpleAttrs, searchKey, emitter, nodeCode);
                        map.put(spec, wrappedExecutor);
                    }
                }
            }
        }
        return map;
    }

    /**
     * 构建全量工具集（带安全上下文传播，用于普通聊天场景）
     *
     * @param allTools        所有注册的 AI 工具实例
     * @param securityContext 当前请求的安全上下文
     * @param enableSearch    是否启用联网搜索工具
     * @param searchKey       联网搜索 API Key
     * @return 工具规格与执行器映射
     */
    public static Map<ToolSpecification, ToolExecutor> getAllTools(
            List<? extends AiTool> allTools,
            SecurityContext securityContext,
            boolean enableSearch,
            String searchKey) {
        String enabledTools = enableSearch ? "web_search" : "";
        return getAllTools(allTools, securityContext, enabledTools, searchKey);
    }

    public static Map<ToolSpecification, ToolExecutor> getAllTools(
            List<? extends AiTool> allTools,
            SecurityContext securityContext,
            String enabledTools,
            String searchKey) {

        Map<ToolSpecification, ToolExecutor> map = new HashMap<>();
        if (allTools == null || allTools.isEmpty()) {
            return map;
        }

        Set<String> allowedClassNames = new HashSet<>();
        if (enabledTools != null && !enabledTools.trim().isEmpty()) {
            for (String toolKey : enabledTools.split(",")) {
                String trimmedKey = toolKey.trim();
                if (trimmedKey.isEmpty()) {
                    continue;
                }
                String className = TOOL_CLASS_MAP.get(trimmedKey);
                if (className != null) {
                    allowedClassNames.add(className);
                } else {
                    // 若不在 TOOL_CLASS_MAP 别名表中，说明传入的是工具类实际类名（如 SysUserTools）
                    allowedClassNames.add(trimmedKey);
                }
            }
        }

        SimpleRequestAttributes simpleAttrs = new SimpleRequestAttributes(RequestContextHolder.getRequestAttributes());

        for (Object toolObj : allTools) {
            Class<?> targetClass = AopUtils.getTargetClass(toolObj);
            String className = targetClass.getSimpleName();

            if (!allowedClassNames.contains(className)) {
                continue;
            }

            if (className.contains("WebSearchTools")
                    && (searchKey == null || searchKey.trim().isEmpty())) {
                continue;
            }

            Method[] methods = targetClass.getDeclaredMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                    ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
                    ToolExecutor originalExecutor = new DefaultToolExecutor(toolObj, method);
                    ToolExecutor wrappedExecutor = new PropagatingExecutor(
                            originalExecutor, securityContext, simpleAttrs, searchKey, null, null);
                    map.put(spec, wrappedExecutor);
                }
            }
        }
        return map;
    }

    // ================================================================
    //  安全上下文传播执行器（核心装饰器）
    // ================================================================

    /**
     * ToolExecutor 装饰器，实现跨线程安全上下文、请求上下文与联网搜索密钥传递
     */
    private static class PropagatingExecutor implements ToolExecutor {
        private final ToolExecutor delegate;
        private final SecurityContext securityContext;
        private final RequestAttributes requestAttributes;
        private final String searchKey;
        private final SseEmitter emitter;
        private final String nodeCode;
        private final Long conversationId;
        private final String fileUrl;

        public PropagatingExecutor(ToolExecutor delegate, SecurityContext securityContext,
                                   RequestAttributes requestAttributes, String searchKey,
                                   SseEmitter emitter, String nodeCode) {
            this.delegate = delegate;
            this.securityContext = securityContext;
            this.requestAttributes = requestAttributes;
            this.searchKey = searchKey;
            this.emitter = emitter;
            this.nodeCode = nodeCode;
            this.conversationId = com.polaris.ai.utils.ChatContextHolder.getConversationId();
            this.fileUrl = com.polaris.ai.utils.ChatContextHolder.getFileUrl();
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            SecurityContext previousContext = SecurityContextHolder.getContext();
            RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
            try {
                // 向前端推送工具调用事件
                if (emitter != null && nodeCode != null) {
                    try {
                        emitter.send(SseEmitter.event().name("node_tool").data(nodeCode + "|" + request.name()));
                    } catch (Exception e) {
                        log.warn(">>> SSE 推送 node_tool 失败: {}", request.name());
                    }
                }

                SecurityContextHolder.setContext(securityContext);
                if (requestAttributes != null) {
                    RequestContextHolder.setRequestAttributes(requestAttributes);
                }
                SearchKeyHolder.set(searchKey);
                com.polaris.ai.utils.ChatContextHolder.setConversationId(conversationId);
                com.polaris.ai.utils.ChatContextHolder.setFileUrl(fileUrl);
                return delegate.execute(request, memoryId);
            } finally {
                com.polaris.ai.utils.ChatContextHolder.clearThreadContext();
                SearchKeyHolder.clear();
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

    // ================================================================
    //  线程安全的 RequestAttributes 快照
    // ================================================================

    /**
     * 纯内存的线程安全 RequestAttributes 实现
     * 在异步线程中传递原始请求属性，防止 Servlet 容器回收后 NPE
     */
    public static class SimpleRequestAttributes implements RequestAttributes {
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
