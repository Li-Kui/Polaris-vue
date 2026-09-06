package com.polaris.ai.tools;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.tools.base.*;
import com.polaris.ai.workflow.spi.WorkflowToolCallObserver;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SecurityContextToolExecutorTest {

    @Test
    void exposesOnlyInternalReadsAndEnforcesResultAndCallLimits() {
        SecurityContext context = securityContext();
        AtomicReference<SecurityContext> current = new AtomicReference<>(context);
        WorkflowToolCallTracker tracker = new WorkflowToolCallTracker(event -> { });

        Map<?, ToolExecutor> tools = SecurityContextToolExecutor.getWorkflowReadOnlyTools(
                List.of(new SampleTool()), "SampleTool", context, current::get,
                1, 1000, () -> false, tracker);

        assertEquals(1, tools.size());
        ToolExecutor executor = tools.values().iterator().next();
        String result = executor.execute(request("readData"), "memory");
        assertTrue(result.endsWith("[工具结果超过安全长度，已截断]"));
        assertEquals(1, tracker.usage().get("toolResultTruncated"));
        assertThrows(SecurityException.class,
                () -> executor.execute(request("readData"), "memory"));
        assertEquals(1, tracker.usage().get("toolCalls"));
        assertEquals(1, tracker.usage().get("toolBlocked"));
    }

    @Test
    void revalidatesPrincipalBeforeEveryExecution() {
        SecurityContext context = securityContext();
        AtomicReference<SecurityContext> current = new AtomicReference<>(context);
        WorkflowToolCallTracker tracker = new WorkflowToolCallTracker(event -> { });
        ToolExecutor executor = SecurityContextToolExecutor.getWorkflowReadOnlyTools(
                        List.of(new SampleTool()), "SampleTool", context, current::get,
                        2, 2000, () -> false, tracker)
                .values().iterator().next();

        current.set(null);

        assertThrows(SecurityException.class,
                () -> executor.execute(request("readData"), "memory"));
        assertEquals(0, tracker.usage().get("toolCalls"));
        assertEquals(1, tracker.usage().get("toolBlocked"));
    }

    @Test
    void blocksCancelledExecutionBeforeStartingTool() {
        SecurityContext context = securityContext();
        WorkflowToolCallTracker tracker = new WorkflowToolCallTracker(event -> { });
        ToolExecutor executor = SecurityContextToolExecutor.getWorkflowReadOnlyTools(
                        List.of(new SampleTool()), "SampleTool", context, () -> context,
                        2, 2000, () -> true, tracker)
                .values().iterator().next();

        assertThrows(CancellationException.class,
                () -> executor.execute(request("readData"), "memory"));
        assertEquals(0, tracker.usage().get("toolCalls"));
        assertEquals(1, tracker.usage().get("toolBlocked"));
    }

    @Test
    void blocksPermissionRevokedAfterToolRegistration() {
        AtomicBoolean allowed = new AtomicBoolean(true);
        SecurityContext context = securityContext(new RestrictedCallerContext(allowed));
        AtomicReference<SecurityContext> current = new AtomicReference<>(context);
        List<WorkflowToolCallObserver.Event> events = new ArrayList<>();
        WorkflowToolCallTracker tracker = new WorkflowToolCallTracker(events::add);
        ToolExecutor executor = SecurityContextToolExecutor.getWorkflowReadOnlyTools(
                        List.of(new RestrictedTool()), "RestrictedTool", context, current::get,
                        2, 2000, () -> false, tracker)
                .values().iterator().next();

        allowed.set(false);

        assertThrows(SecurityException.class,
                () -> executor.execute(request("readRestrictedData"), "memory"));
        assertEquals(0, tracker.usage().get("toolCalls"));
        assertEquals("PERMISSION_REVOKED", events.get(0).reasonCode());
    }

    @Test
    void exposesPlatformToolOnlyToPlatformCaller() {
        SecurityContext platform = securityContext();
        SecurityContext admin = securityContext(new NonPlatformCallerContext());
        PlatformOnlyTool tool = new PlatformOnlyTool();

        assertEquals(1, SecurityContextToolExecutor.getWorkflowReadOnlyTools(
                List.of(tool), "PlatformOnlyTool", platform, () -> platform,
                2, 2000, () -> false, new WorkflowToolCallTracker(event -> { })).size());
        assertTrue(SecurityContextToolExecutor.getWorkflowReadOnlyTools(
                List.of(tool), "PlatformOnlyTool", admin, () -> admin,
                2, 2000, () -> false, new WorkflowToolCallTracker(event -> { })).isEmpty());
        assertEquals(1, SecurityContextToolExecutor.getAllTools(
                List.of(tool), platform, "PlatformOnlyTool", null).size());
        assertTrue(SecurityContextToolExecutor.getAllTools(
                List.of(tool), admin, "PlatformOnlyTool", null).isEmpty());
        assertEquals(1, SecurityContextToolExecutor.getFilteredTools(
                List.of(tool), "PlatformOnlyTool", platform, null, null, null).size());
        assertTrue(SecurityContextToolExecutor.getFilteredTools(
                List.of(tool), "PlatformOnlyTool", admin, null, null, null).isEmpty());
    }

    private ToolExecutionRequest request(String name) {
        return ToolExecutionRequest.builder()
                .id("call-1")
                .name(name)
                .arguments("{}")
                .build();
    }

    private SecurityContext securityContext() {
        return securityContext(new TestCallerContext());
    }

    private SecurityContext securityContext(CallerContext caller) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                caller, null, List.of()));
        return context;
    }

    @AiAgentTool("测试工具")
    static class SampleTool implements AiTool {

        @Tool("读取测试数据")
        @AiToolPermission(sideEffect = ToolSideEffect.READ)
        public String readData() {
            return "x".repeat(1500);
        }

        @Tool("修改测试数据")
        @AiToolPermission(sideEffect = ToolSideEffect.WRITE)
        public String writeData() {
            return "updated";
        }

        @Tool("读取外部数据")
        @AiToolPermission(
                sideEffect = ToolSideEffect.READ,
                dataBoundary = ToolDataBoundary.EXTERNAL)
        public String readExternalData() {
            return "external";
        }

        @Tool("未分类工具")
        @AiToolPermission
        public String unclassifiedData() {
            return "unknown";
        }

        @Tool("缺少安全声明")
        public String missingPermissionData() {
            return "unknown";
        }
    }

    @AiAgentTool("受限测试工具")
    static class RestrictedTool implements AiTool {

        @Tool("读取受限测试数据")
        @AiToolPermission(value = "sample:read", sideEffect = ToolSideEffect.READ)
        public String readRestrictedData() {
            return "restricted";
        }
    }

    @AiAgentTool(value = "中台测试工具", scope = ToolScope.PLATFORM)
    static class PlatformOnlyTool implements AiTool {

        @Tool("读取中台测试数据")
        @AiToolPermission(sideEffect = ToolSideEffect.READ)
        public String readPlatformData() {
            return "platform";
        }
    }

    static class TestCallerContext implements CallerContext {
        @Override
        public Long getUserId() {
            return 1L;
        }

        @Override
        public String getTenantId() {
            return "8";
        }

        @Override
        public String getUsername() {
            return "tester";
        }

        @Override
        public Long getDeptId() {
            return null;
        }

        @Override
        public boolean isSuperAdmin() {
            return false;
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }
    }

    static class NonPlatformCallerContext extends TestCallerContext {
        @Override
        public String getTenantId() {
            return null;
        }
    }

    static class RestrictedCallerContext extends TestCallerContext {
        private final AtomicBoolean allowed;

        RestrictedCallerContext(AtomicBoolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public boolean hasPermission(String permission) {
            return allowed.get() && "sample:read".equals(permission);
        }
    }
}
