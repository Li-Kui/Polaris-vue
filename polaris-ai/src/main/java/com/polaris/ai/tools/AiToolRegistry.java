package com.polaris.ai.tools;

import com.polaris.ai.tools.base.AiTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
}
