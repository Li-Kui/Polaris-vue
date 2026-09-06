package com.polaris.ai.tools;

import com.polaris.ai.tools.base.*;
import com.polaris.ai.utils.ToolSseHolder;
import com.polaris.ai.workflow.spi.WorkflowPlatformDataProvider;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 中台智能体可用的当前租户只读工具。 */
@Component
@AiAgentTool(value = "当前租户概览", scope = ToolScope.PLATFORM)
public class PlatformTenantTools implements AiTool {

    private final WorkflowPlatformDataProvider dataProvider;

    public PlatformTenantTools(Optional<WorkflowPlatformDataProvider> dataProvider) {
        this.dataProvider = dataProvider.orElse(null);
    }

    @Tool("查询当前登录身份所属租户的名称、编码和 Token 用量。不返回联系人信息，也不能查询其他租户。")
    @AiToolPermission(sideEffect = ToolSideEffect.READ)
    public WorkflowPlatformDataProvider.TenantUsageSummary getCurrentTenantUsage() {
        ToolSseHolder.ensureActive();
        if (dataProvider == null) {
            throw new IllegalStateException("当前运行环境未提供租户数据");
        }
        WorkflowPlatformDataProvider.TenantUsageSummary result = dataProvider
                .currentTenantUsage()
                .orElseThrow(() -> new SecurityException("当前租户不可用或无权访问"));
        ToolSseHolder.ensureActive();
        return result;
    }
}
