package com.polaris.ai.tools;

import com.polaris.ai.tools.base.AiAgentTool;
import com.polaris.ai.tools.base.AiToolPermission;
import com.polaris.ai.tools.base.ToolScope;
import com.polaris.ai.tools.base.ToolSideEffect;
import com.polaris.ai.workflow.spi.WorkflowPlatformDataProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlatformTenantToolsTest {

    @Test
    void returnsProviderSummaryWithoutAcceptingTenantIdentifier() throws Exception {
        var summary = new WorkflowPlatformDataProvider.TenantUsageSummary(
                8L, "示例租户", "demo", 10000L, 2300L);
        PlatformTenantTools tools = new PlatformTenantTools(Optional.of(() -> Optional.of(summary)));

        assertEquals(summary, tools.getCurrentTenantUsage());
        Method method = PlatformTenantTools.class.getMethod("getCurrentTenantUsage");
        assertEquals(0, method.getParameterCount());
        assertEquals(ToolScope.PLATFORM,
                PlatformTenantTools.class.getAnnotation(AiAgentTool.class).scope());
        assertEquals(ToolSideEffect.READ,
                method.getAnnotation(AiToolPermission.class).sideEffect());
    }

    @Test
    void refusesExecutionWhenPlatformDataIsUnavailable() {
        PlatformTenantTools tools = new PlatformTenantTools(Optional.empty());

        assertThrows(IllegalStateException.class, tools::getCurrentTenantUsage);
    }
}
