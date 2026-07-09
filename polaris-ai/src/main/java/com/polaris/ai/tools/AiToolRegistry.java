package com.polaris.ai.tools;

import com.polaris.ai.tools.base.AiTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;

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
        return SecurityContextToolExecutor.getAllTools(aiTools, securityContext, enableSearch, searchKey);
    }
}
