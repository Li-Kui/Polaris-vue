package com.polaris.ai.tools;

import com.polaris.ai.tools.base.AiTool;
import com.polaris.ai.tools.base.AiToolPermission;
import com.polaris.ai.utils.SearchKeyHolder;
import com.polaris.ai.utils.ToolSseHolder;
import com.polaris.ai.workflow.event.WorkflowSsePublisher;
import com.polaris.common.utils.SecurityUtils;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * 统一的安全上下文传播工具执行器
 * <p>
 * 为普通聊天和 LangGraph4j 工作流统一提供 SecurityContext、RequestAttributes
 * 与 SearchKey 的跨线程传递能力。
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
    public static ToolExecutor wrapExecutor(
            ToolExecutor originalExecutor, Method method,
            SecurityContext securityContext, String searchKey) {
        return wrapExecutor(originalExecutor, method, securityContext, searchKey, null);
    }

    public static ToolExecutor wrapExecutor(
            ToolExecutor originalExecutor, Method method,
            SecurityContext securityContext, String searchKey, SseEmitter emitter) {
        String requiredPermission = requiredPermission(method);
        if (requiredPermission == null || !hasPermission(securityContext, requiredPermission)) {
            return null;
        }
        SimpleRequestAttributes simpleAttrs = new SimpleRequestAttributes(RequestContextHolder.getRequestAttributes());
        return new PropagatingExecutor(originalExecutor, securityContext, simpleAttrs, searchKey,
                emitter, null, requiredPermission, null, null, null, null, null);
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
        return getFilteredTools(allTools, toolsConfig, securityContext, searchKey,
                emitter, nodeCode, null, null, null, null);
    }

    public static Map<ToolSpecification, ToolExecutor> getFilteredTools(
            List<? extends AiTool> allTools,
            String toolsConfig,
            SecurityContext securityContext,
            String searchKey,
            SseEmitter emitter,
            String nodeCode,
            WorkflowSsePublisher eventPublisher,
            String executionId) {

        return getFilteredTools(allTools, toolsConfig, securityContext, searchKey,
                emitter, nodeCode, eventPublisher, executionId, null, null);
    }

    public static Map<ToolSpecification, ToolExecutor> getFilteredTools(
            List<? extends AiTool> allTools,
            String toolsConfig,
            SecurityContext securityContext,
            String searchKey,
            SseEmitter emitter,
            String nodeCode,
            WorkflowSsePublisher eventPublisher,
            String executionId,
            AtomicBoolean workflowCancelled,
            AtomicBoolean nodeCancelled) {

        return getFilteredTools(allTools, toolsConfig, securityContext, searchKey,
                emitter, nodeCode, eventPublisher, executionId,
                workflowCancelled, nodeCancelled, null);
    }

    public static Map<ToolSpecification, ToolExecutor> getFilteredTools(
            List<? extends AiTool> allTools,
            String toolsConfig,
            SecurityContext securityContext,
            String searchKey,
            SseEmitter emitter,
            String nodeCode,
            WorkflowSsePublisher eventPublisher,
            String executionId,
            AtomicBoolean workflowCancelled,
            AtomicBoolean nodeCancelled,
            BooleanSupplier cancellationProbe) {

        Map<ToolSpecification, ToolExecutor> map = new HashMap<>();
        if (toolsConfig == null || toolsConfig.trim().isEmpty() || allTools == null || allTools.isEmpty()) {
            return map;
        }

        Set<String> toolNames = resolveToolClassNames(toolsConfig);

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
                        String requiredPermission = requiredPermission(method);
                        if (requiredPermission == null) {
                            log.warn(">>> AI 工具方法缺少 @AiToolPermission，已拒绝暴露: {}.{}",
                                    className, method.getName());
                            continue;
                        }
                        if (!hasPermission(securityContext, requiredPermission)) {
                            log.debug(">>> AI 工具方法因权限不足未暴露: {}.{} ({})",
                                    className, method.getName(), requiredPermission);
                            continue;
                        }
                        ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
                        ToolExecutor originalExecutor = new DefaultToolExecutor(toolObj, method);
                        ToolExecutor wrappedExecutor = new PropagatingExecutor(
                                originalExecutor, securityContext, simpleAttrs, searchKey, emitter, nodeCode,
                                requiredPermission, eventPublisher, executionId,
                                workflowCancelled, nodeCancelled, cancellationProbe);
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

        return getAllTools(allTools, securityContext, enabledTools, searchKey, null);
    }

    public static Map<ToolSpecification, ToolExecutor> getAllTools(
            List<? extends AiTool> allTools,
            SecurityContext securityContext,
            String enabledTools,
            String searchKey,
            SseEmitter emitter) {

        Map<ToolSpecification, ToolExecutor> map = new HashMap<>();
        if (allTools == null || allTools.isEmpty()) {
            return map;
        }

        Set<String> allowedClassNames = resolveToolClassNames(enabledTools);

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
                    String requiredPermission = requiredPermission(method);
                    if (requiredPermission == null) {
                        log.warn(">>> AI 工具方法缺少 @AiToolPermission，已拒绝暴露: {}.{}",
                                className, method.getName());
                        continue;
                    }
                    if (!hasPermission(securityContext, requiredPermission)) {
                        log.debug(">>> AI 工具方法因权限不足未暴露: {}.{} ({})",
                                className, method.getName(), requiredPermission);
                        continue;
                    }
                    ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
                    ToolExecutor originalExecutor = new DefaultToolExecutor(toolObj, method);
                    ToolExecutor wrappedExecutor = new PropagatingExecutor(
                            originalExecutor, securityContext, simpleAttrs, searchKey, emitter, null,
                            requiredPermission, null, null, null, null, null);
                    map.put(spec, wrappedExecutor);
                }
            }
        }
        return map;
    }

    /** 将工具别名或类名配置解析为稳定的工具类简单名集合。 */
    public static Set<String> resolveToolClassNames(String toolsConfig) {
        Set<String> classNames = new LinkedHashSet<>();
        if (toolsConfig == null || toolsConfig.isBlank()) {
            return classNames;
        }
        for (String configuredName : toolsConfig.split(",")) {
            String name = configuredName.trim();
            if (!name.isEmpty()) {
                classNames.add(TOOL_CLASS_MAP.getOrDefault(name, name));
            }
        }
        return classNames;
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
        private final String requiredPermission;
        private final WorkflowSsePublisher eventPublisher;
        private final String executionId;
        private final AtomicBoolean workflowCancelled;
        private final AtomicBoolean nodeCancelled;
        private final BooleanSupplier cancellationProbe;
        private final Long conversationId;
        private final String fileUrl;

        public PropagatingExecutor(ToolExecutor delegate, SecurityContext securityContext,
                                   RequestAttributes requestAttributes, String searchKey,
                                   SseEmitter emitter, String nodeCode, String requiredPermission,
                                   WorkflowSsePublisher eventPublisher, String executionId,
                                   AtomicBoolean workflowCancelled, AtomicBoolean nodeCancelled,
                                   BooleanSupplier cancellationProbe) {
            this.delegate = delegate;
            this.securityContext = securityContext;
            this.requestAttributes = requestAttributes;
            this.searchKey = searchKey;
            this.emitter = emitter;
            this.nodeCode = nodeCode;
            this.requiredPermission = requiredPermission;
            this.eventPublisher = eventPublisher;
            this.executionId = executionId;
            this.workflowCancelled = workflowCancelled;
            this.nodeCancelled = nodeCancelled;
            this.cancellationProbe = cancellationProbe;
            this.conversationId = com.polaris.ai.utils.ChatContextHolder.getConversationId();
            this.fileUrl = com.polaris.ai.utils.ChatContextHolder.getFileUrl();
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            SecurityContext previousContext = SecurityContextHolder.getContext();
            RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
            try {
                ensureNotCancelled();
                SecurityContextHolder.setContext(securityContext);
                if (!hasPermission(securityContext, requiredPermission)) {
                    throw new SecurityException("无权执行 AI 工具 " + request.name()
                            + "，缺少权限: " + requiredPermission);
                }

                if (emitter != null && nodeCode != null) {
                    if (eventPublisher != null && executionId != null) {
                        eventPublisher.send(emitter, executionId, "node_tool", nodeCode,
                                Map.of("toolName", request.name()));
                    } else {
                        try {
                            emitter.send(SseEmitter.event().name("node_tool")
                                    .data(nodeCode + "|" + request.name()));
                        } catch (Exception e) {
                            log.warn(">>> SSE 推送 node_tool 失败: {}", request.name());
                        }
                    }
                }

                if (requestAttributes != null) {
                    RequestContextHolder.setRequestAttributes(requestAttributes);
                }
                SearchKeyHolder.set(searchKey);
                if (eventPublisher != null && executionId != null) {
                    ToolSseHolder.setWorkflow(
                            emitter, eventPublisher, executionId, nodeCode,
                            workflowCancelled, nodeCancelled, cancellationProbe);
                } else {
                    ToolSseHolder.set(emitter);
                }
                com.polaris.ai.utils.ChatContextHolder.setConversationId(conversationId);
                com.polaris.ai.utils.ChatContextHolder.setFileUrl(fileUrl);
                ensureNotCancelled();
                return delegate.execute(request, memoryId);
            } finally {
                com.polaris.ai.utils.ChatContextHolder.clearThreadContext();
                ToolSseHolder.clear();
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

        private void ensureNotCancelled() {
            if (Thread.currentThread().isInterrupted()
                    || (workflowCancelled != null && workflowCancelled.get())
                    || (nodeCancelled != null && nodeCancelled.get())
                    || (cancellationProbe != null && cancellationProbe.getAsBoolean())) {
                throw new CancellationException("工作流工具执行已取消");
            }
        }
    }

    private static String requiredPermission(Method method) {
        AiToolPermission permission = method.getAnnotation(AiToolPermission.class);
        return permission == null ? null : permission.value();
    }

    private static boolean hasPermission(SecurityContext securityContext, String permission) {
        if (securityContext == null || securityContext.getAuthentication() == null
                || !securityContext.getAuthentication().isAuthenticated()
                || securityContext.getAuthentication() instanceof AnonymousAuthenticationToken) {
            return false;
        }
        if (permission == null) {
            return false;
        }
        if (permission.isBlank()) {
            return true;
        }
        List<String> permissions = securityContext.getAuthentication().getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toList());
        return SecurityUtils.hasPermi(permissions, permission);
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
