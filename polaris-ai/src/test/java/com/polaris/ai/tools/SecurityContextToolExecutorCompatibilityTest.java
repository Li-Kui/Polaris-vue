package com.polaris.ai.tools;

import com.polaris.ai.tools.base.AiToolPermission;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SecurityContextToolExecutorCompatibilityTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forwardsInvocationContextAndPreservesRichResult() throws Exception {
        SecurityContext toolContext = authenticatedContext("tool-user");
        SecurityContext previousContext = authenticatedContext("request-user");
        SecurityContextHolder.setContext(previousContext);
        InvocationContext invocationContext = invocationContext("memory-42");
        AtomicReference<InvocationContext> receivedContext = new AtomicReference<>();
        AtomicReference<Object> receivedPrincipal = new AtomicReference<>();
        ToolExecutionResult expected = ToolExecutionResult.builder()
                .result(Map.of("status", "ok"))
                .resultText("ok")
                .attributes(Map.of("source", "compatibility-test"))
                .build();
        ToolExecutor delegate = new ToolExecutor() {
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return "legacy";
            }

            @Override
            public ToolExecutionResult executeWithContext(
                    ToolExecutionRequest request, InvocationContext context) {
                receivedContext.set(context);
                receivedPrincipal.set(SecurityContextHolder.getContext()
                        .getAuthentication().getPrincipal());
                return expected;
            }
        };
        ToolExecutor wrapped = wrap(delegate, toolContext);

        ToolExecutionResult actual = wrapped.executeWithContext(request(), invocationContext);

        assertSame(expected, actual);
        assertSame(invocationContext, receivedContext.get());
        assertEquals("tool-user", receivedPrincipal.get());
        assertSame(previousContext, SecurityContextHolder.getContext());
    }

    @Test
    void supportsAsyncExecutionOnTheContextAwareWorkerThread() throws Exception {
        SecurityContext toolContext = authenticatedContext("async-tool-user");
        InvocationContext invocationContext = invocationContext("memory-async");
        AtomicReference<Object> receivedPrincipal = new AtomicReference<>();
        ToolExecutor delegate = new ToolExecutor() {
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return "legacy";
            }

            @Override
            public ToolExecutionResult executeWithContext(
                    ToolExecutionRequest request, InvocationContext context) {
                receivedPrincipal.set(SecurityContextHolder.getContext()
                        .getAuthentication().getPrincipal());
                return ToolExecutionResult.builder().resultText("async-ok").build();
            }
        };
        ToolExecutor wrapped = wrap(delegate, toolContext);

        ToolExecutionResult result = wrapped.executeAsync(request(), invocationContext).join();

        assertEquals("async-ok", result.resultText());
        assertEquals("async-tool-user", receivedPrincipal.get());
    }

    private ToolExecutor wrap(ToolExecutor delegate, SecurityContext context) throws Exception {
        Method method = TestTool.class.getDeclaredMethod("execute");
        return SecurityContextToolExecutor.wrapExecutor(delegate, method, context, null);
    }

    private SecurityContext authenticatedContext(String principal) {
        return new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                principal, "n/a", List.of()));
    }

    private ToolExecutionRequest request() {
        return ToolExecutionRequest.builder()
                .id("call-1")
                .name("test-tool")
                .arguments("{}")
                .build();
    }

    private InvocationContext invocationContext(Object memoryId) {
        return new InvocationContext() {
            @Override
            public UUID invocationId() {
                return UUID.fromString("00000000-0000-0000-0000-000000000001");
            }

            @Override
            public String interfaceName() {
                return "TestAiService";
            }

            @Override
            public String methodName() {
                return "chat";
            }

            @Override
            public List<Object> methodArguments() {
                return List.of();
            }

            @Override
            public Object chatMemoryId() {
                return memoryId;
            }

            @Override
            public InvocationParameters invocationParameters() {
                return null;
            }

            @Override
            public Instant timestamp() {
                return Instant.EPOCH;
            }
        };
    }

    private static final class TestTool {
        @AiToolPermission
        public void execute() {
        }
    }
}
