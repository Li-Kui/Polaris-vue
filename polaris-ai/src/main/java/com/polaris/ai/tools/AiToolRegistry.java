package com.polaris.ai.tools;

import com.polaris.ai.tools.base.AiTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 运行时上下文工具注册器
 * 
 * @author polaris
 */
@Component
public class AiToolRegistry {

    @Autowired(required = false)
    private List<AiTool> aiTools;

    /**
     * 通用反射解析 AI 工具并绑定当前线程安全上下文与请求上下文
     */
    public Map<ToolSpecification, ToolExecutor> getContextAwareTools(SecurityContext securityContext, boolean enableSearch, String searchKey) {
        Map<ToolSpecification, ToolExecutor> map = new HashMap<>();
        if (aiTools == null || aiTools.isEmpty()) {
            return map;
        }

        SimpleRequestAttributes simpleAttrs = new SimpleRequestAttributes(RequestContextHolder.getRequestAttributes());

        for (Object toolObj : aiTools) {
            Class<?> targetClass = AopUtils.getTargetClass(toolObj);
            if (targetClass.getSimpleName().contains("WebSearchTools") && !enableSearch) {
                continue;
            }
            Method[] methods = targetClass.getDeclaredMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                    ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
                    
                    // 特殊过滤：如果是 WebSearchTools 且没有 searchKey，则跳过不注册
                    if (targetClass.getSimpleName().contains("WebSearchTools") && (searchKey == null || searchKey.trim().isEmpty())) {
                        continue;
                    }
                    
                    ToolExecutor originalExecutor = new DefaultToolExecutor(toolObj, method);
                    ToolExecutor wrappedExecutor = new SecurityContextPropagatingToolExecutor(originalExecutor, securityContext, simpleAttrs, searchKey);
                    map.put(spec, wrappedExecutor);
                }
            }
        }
        return map;
    }

    /**
     * 通用 ToolExecutor 装饰器，实现跨线程安全上下文、请求上下文与联网搜索密钥传递
     */
    private static class SecurityContextPropagatingToolExecutor implements ToolExecutor {
        private final ToolExecutor delegate;
        private final SecurityContext securityContext;
        private final RequestAttributes requestAttributes;
        private final String searchKey;

        public SecurityContextPropagatingToolExecutor(ToolExecutor delegate, SecurityContext securityContext, RequestAttributes requestAttributes, String searchKey) {
            this.delegate = delegate;
            this.securityContext = securityContext;
            this.requestAttributes = requestAttributes;
            this.searchKey = searchKey;
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            SecurityContext previousContext = SecurityContextHolder.getContext();
            RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
            try {
                SecurityContextHolder.setContext(securityContext);
                if (requestAttributes != null) {
                    RequestContextHolder.setRequestAttributes(requestAttributes);
                }
                // 绑定联网搜索 Key 到执行线程中
                com.polaris.ai.utils.SearchKeyHolder.set(searchKey);
                return delegate.execute(request, memoryId);
            } finally {
                // 清理联网 Key，防止线程池污染
                com.polaris.ai.utils.SearchKeyHolder.clear();
                SecurityContextHolder.setContext(previousContext);
                RequestContextHolder.setRequestAttributes(previousAttributes);
            }
        }
    }

    /**
     * 纯内存的线程安全 RequestAttributes 实现，专门用于在慢速请求异步线程中传递属性
     */
    private static class SimpleRequestAttributes implements RequestAttributes {
        private final Map<String, Object> attributes = new java.util.concurrent.ConcurrentHashMap<>();

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
