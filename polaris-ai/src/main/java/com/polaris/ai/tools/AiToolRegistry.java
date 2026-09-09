package com.polaris.ai.tools;

import com.polaris.ai.tools.base.*;
import com.polaris.ai.workflow.spi.WorkflowToolCallObserver;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * AI 运行时上下文工具注册器
 * 
 * @author polaris
 */
@Component
public class AiToolRegistry {

    @Autowired(required = false)
    private List<AiTool> aiTools;

    @Autowired
    private com.polaris.ai.tools.rag.ToolRetriever toolRetriever;

    /**
     * 通用反射解析 AI 工具并绑定当前线程安全上下文与请求上下文
     */
    public Map<ToolSpecification, ToolExecutor> getContextAwareTools(SecurityContext securityContext, boolean enableSearch, String searchKey) {
        return SecurityContextToolExecutor.getAllTools(aiTools, securityContext, enableSearch, searchKey);
    }

    /**
     * 根据当前会话模型启用的工具白名单动态装配工具
     */
    public Map<ToolSpecification, ToolExecutor> getContextAwareTools(SecurityContext securityContext, String enabledTools, String searchKey) {
        return SecurityContextToolExecutor.getAllTools(aiTools, securityContext, enabledTools, searchKey);
    }

    public Map<ToolSpecification, ToolExecutor> getContextAwareTools(SecurityContext securityContext, String enabledTools, String searchKey, SseEmitter emitter) {
        return SecurityContextToolExecutor.getAllTools(aiTools, securityContext, enabledTools, searchKey, emitter);
    }

    /** 构建工作流智能体可用的内部只读工具；其他工具在注册阶段即被排除。 */
    public WorkflowToolSet getWorkflowReadOnlyTools(
            SecurityContext securityContext,
            Supplier<SecurityContext> securityContextSupplier,
            String enabledTools,
            int maxToolCalls,
            int maxToolResultChars,
            BooleanSupplier cancellationProbe,
            WorkflowToolCallObserver observer) {
        WorkflowToolCallTracker tracker = new WorkflowToolCallTracker(observer);
        Map<ToolSpecification, ToolExecutor> tools =
                SecurityContextToolExecutor.getWorkflowReadOnlyTools(
                        aiTools, enabledTools, securityContext, securityContextSupplier,
                        maxToolCalls, maxToolResultChars, cancellationProbe, tracker);
        return new WorkflowToolSet(tools, tracker);
    }

    /**
     * 根据用户输入的提问及安全上下文，利用 Tool-RAG 动态按需匹配 Top-N 工具
     */
    public Map<ToolSpecification, ToolExecutor> getRetrievedTools(String userPrompt, SecurityContext securityContext, String searchKey, int topN) {
        return toolRetriever.retrieveTools(userPrompt, securityContext, searchKey, topN);
    }

    public Map<ToolSpecification, ToolExecutor> getRetrievedTools(String userPrompt, SecurityContext securityContext, String searchKey, int topN, SseEmitter emitter) {
        return toolRetriever.retrieveTools(userPrompt, securityContext, searchKey, topN, emitter);
    }

    /**
     * 根据多轮对话历史中出现过的工具名称集合，装配并保留 Slim 瘦身模式的历史工具 Schema（第二阶：降维立省 80% Token 消耗）
     */
    public Map<ToolSpecification, ToolExecutor> getSlimToolsForHistory(java.util.Set<String> historicalToolNames, SecurityContext securityContext, String searchKey) {
        return toolRetriever.getSlimToolsByNames(historicalToolNames, securityContext, searchKey);
    }

    public Map<ToolSpecification, ToolExecutor> getSlimToolsForHistory(java.util.Set<String> historicalToolNames, SecurityContext securityContext, String searchKey, SseEmitter emitter) {
        return toolRetriever.getSlimToolsByNames(historicalToolNames, securityContext, searchKey, emitter);
    }

    /** 解析智能体已绑定工具的可执行方法数量和最高副作用等级。 */
    public ConfiguredToolSummary describeConfiguredTools(String toolsConfig) {
        Set<String> classNames = SecurityContextToolExecutor.resolveToolClassNames(toolsConfig);
        if (classNames.isEmpty() || aiTools == null || aiTools.isEmpty()) {
            return new ConfiguredToolSummary(0, 0, 0, 0, 0, 0, 0, 0);
        }
        int methodCount = 0;
        int internalReadMethodCount = 0;
        int writeMethodCount = 0;
        int externalMethodCount = 0;
        int unclassifiedMethodCount = 0;
        int universalInternalReadMethodCount = 0;
        int adminInternalReadMethodCount = 0;
        int platformInternalReadMethodCount = 0;
        Set<String> matchedClasses = new HashSet<>();
        for (AiTool tool : aiTools) {
            Class<?> targetClass = AopUtils.getTargetClass(tool);
            if (!classNames.contains(targetClass.getSimpleName())) continue;
            matchedClasses.add(targetClass.getSimpleName());
            AiAgentTool toolDefinition = targetClass.getAnnotation(AiAgentTool.class);
            ToolScope toolScope = toolDefinition == null
                    ? ToolScope.UNIVERSAL : toolDefinition.scope();
            for (Method method : targetClass.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Tool.class)) continue;
                methodCount += 1;
                AiToolPermission permission = method.getAnnotation(AiToolPermission.class);
                if (permission == null) {
                    unclassifiedMethodCount += 1;
                    continue;
                }
                if (permission.sideEffect() == ToolSideEffect.READ
                        && permission.dataBoundary() == ToolDataBoundary.INTERNAL) {
                    internalReadMethodCount += 1;
                    if (toolScope == ToolScope.ADMIN_ONLY) {
                        adminInternalReadMethodCount += 1;
                    } else if (toolScope == ToolScope.PLATFORM) {
                        platformInternalReadMethodCount += 1;
                    } else {
                        universalInternalReadMethodCount += 1;
                    }
                }
                if (permission.sideEffect() == ToolSideEffect.WRITE) writeMethodCount += 1;
                if (permission.sideEffect() == ToolSideEffect.UNKNOWN) unclassifiedMethodCount += 1;
                if (permission.dataBoundary() == ToolDataBoundary.EXTERNAL) externalMethodCount += 1;
            }
        }
        int missingToolCount = Math.max(0, classNames.size() - matchedClasses.size());
        methodCount += missingToolCount;
        unclassifiedMethodCount += missingToolCount;
        return new ConfiguredToolSummary(
                methodCount, internalReadMethodCount, writeMethodCount,
                externalMethodCount, unclassifiedMethodCount,
                universalInternalReadMethodCount, adminInternalReadMethodCount,
                platformInternalReadMethodCount);
    }

    public record ConfiguredToolSummary(
            int methodCount,
            int internalReadMethodCount,
            int writeMethodCount,
            int externalMethodCount,
            int unclassifiedMethodCount,
            int universalInternalReadMethodCount,
            int adminInternalReadMethodCount,
            int platformInternalReadMethodCount) {
        public boolean hasTools() {
            return methodCount > 0;
        }

        public boolean hasWriteTools() {
            return writeMethodCount > 0;
        }

        public boolean hasInternalReadTools() {
            return internalReadMethodCount > 0;
        }

        public int internalReadMethodCountFor(String principalType) {
            if ("ADMIN".equals(principalType)) {
                return universalInternalReadMethodCount + adminInternalReadMethodCount;
            }
            if ("PLATFORM_USER".equals(principalType)
                    || "API_KEY".equals(principalType)) {
                return universalInternalReadMethodCount + platformInternalReadMethodCount;
            }
            return 0;
        }

        public boolean hasExternalTools() {
            return externalMethodCount > 0;
        }

        public boolean hasUnclassifiedTools() {
            return unclassifiedMethodCount > 0;
        }
    }

    public record WorkflowToolSet(
            Map<ToolSpecification, ToolExecutor> tools,
            WorkflowToolCallTracker tracker) {
        public WorkflowToolSet {
            tools = tools == null ? Map.of() : Map.copyOf(tools);
            tracker = tracker == null
                    ? new WorkflowToolCallTracker(WorkflowToolCallObserver.NONE) : tracker;
        }

        public int callCount() {
            return tracker.usage().get("toolCalls").intValue();
        }

        public Map<String, Number> usage() {
            return tracker.usage();
        }
    }
}
