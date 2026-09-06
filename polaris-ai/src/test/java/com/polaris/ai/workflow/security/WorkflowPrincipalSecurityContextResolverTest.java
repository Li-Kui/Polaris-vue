package com.polaris.ai.workflow.security;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.polaris.ai.workflow.spi.WorkflowCancellation;
import com.polaris.ai.workflow.spi.WorkflowNodeContext;
import com.polaris.ai.workflow.spi.WorkflowToolCallObserver;
import com.polaris.common.core.domain.entity.SysUser;
import com.polaris.system.service.ISysMenuService;
import com.polaris.system.service.ISysUserService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowPrincipalSecurityContextResolverTest {

    @Test
    void reloadsActiveAdminUserAndCurrentPermissions() {
        SysUser user = new SysUser();
        user.setUserId(2L);
        user.setDeptId(10L);
        user.setUserName("auditor");
        user.setStatus("0");
        user.setDelFlag("0");
        ISysUserService userService = service(ISysUserService.class,
                (method, arguments) -> "selectUserById".equals(method) ? user : null);
        ISysMenuService menuService = service(ISysMenuService.class,
                (method, arguments) -> "selectMenuPermsByUserId".equals(method)
                        ? Set.of("system:user:list") : null);
        WorkflowPrincipalSecurityContextResolver resolver =
                new WorkflowPrincipalSecurityContextResolver(userService, menuService);

        var resolved = resolver.resolve(context(null, "ADMIN", "2"));

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().getAuthentication().getAuthorities().stream()
                .anyMatch(item -> "system:user:list".equals(item.getAuthority())));
    }

    @Test
    void rejectsTenantScopedAdminBeforeLoadingSystemUser() {
        AtomicInteger userLoads = new AtomicInteger();
        ISysUserService userService = service(ISysUserService.class,
                (method, arguments) -> {
                    userLoads.incrementAndGet();
                    return null;
                });
        ISysMenuService menuService = service(ISysMenuService.class,
                (method, arguments) -> null);
        WorkflowPrincipalSecurityContextResolver resolver =
                new WorkflowPrincipalSecurityContextResolver(userService, menuService);

        assertTrue(resolver.resolve(context(8L, "ADMIN", "2")).isEmpty());
        assertTrue(userLoads.get() == 0);
    }

    private WorkflowNodeContext context(
            Long tenantId, String principalType, String principalId) {
        return new WorkflowNodeContext(
                "execution", "node-run", 1, tenantId, principalType, principalId,
                JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.objectNode(),
                Map.of(), WorkflowCancellation.NONE, WorkflowToolCallObserver.NONE);
    }

    @SuppressWarnings("unchecked")
    private <T> T service(Class<T> type, ServiceCall call) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> call.invoke(method.getName(), arguments));
    }

    @FunctionalInterface
    private interface ServiceCall {
        Object invoke(String method, Object[] arguments);
    }
}
