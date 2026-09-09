package com.polaris.platform.auth;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.platform.domain.PlatformApiKey;
import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.domain.Tenant;
import com.polaris.platform.service.IPlatformApiKeyService;
import com.polaris.platform.service.IPlatformUserService;
import com.polaris.platform.service.ITenantService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformWorkflowPrincipalContextProviderTest {

    @Test
    void restoresOnlyActiveUserInsideActiveTenant() {
        Tenant tenant = new Tenant();
        tenant.setTenantId(8L);
        tenant.setStatus("0");
        PlatformUser user = new PlatformUser();
        user.setId(12L);
        user.setTenantId(8L);
        user.setUsername("operator");
        user.setRole("admin");
        user.setStatus("0");
        IPlatformUserService userService = service(IPlatformUserService.class,
                (method, arguments) -> "selectUserById".equals(method) ? user : null);
        IPlatformApiKeyService apiKeyService = service(IPlatformApiKeyService.class,
                (method, arguments) -> null);
        ITenantService tenantService = service(ITenantService.class,
                (method, arguments) -> "selectTenantById".equals(method) ? tenant : null);
        PlatformWorkflowPrincipalContextProvider provider =
                new PlatformWorkflowPrincipalContextProvider(
                        userService, apiKeyService, tenantService);

        var resolved = provider.resolve(8L, "PLATFORM_USER", "12");

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().isTenantAdmin());
    }

    @Test
    void rejectsExpiredApiKey() {
        Tenant tenant = new Tenant();
        tenant.setTenantId(8L);
        tenant.setStatus("0");
        PlatformApiKey apiKey = new PlatformApiKey();
        apiKey.setId(21L);
        apiKey.setTenantId(8L);
        apiKey.setStatus("0");
        apiKey.setExpireTime(new Date(System.currentTimeMillis() - 1000));
        IPlatformUserService userService = service(IPlatformUserService.class,
                (method, arguments) -> null);
        IPlatformApiKeyService apiKeyService = service(IPlatformApiKeyService.class,
                (method, arguments) -> "selectApiKeyById".equals(method) ? apiKey : null);
        ITenantService tenantService = service(ITenantService.class,
                (method, arguments) -> "selectTenantById".equals(method) ? tenant : null);
        PlatformWorkflowPrincipalContextProvider provider =
                new PlatformWorkflowPrincipalContextProvider(
                        userService, apiKeyService, tenantService);

        assertTrue(provider.resolve(8L, "API_KEY", "21").isEmpty());
    }

    @Test
    void readsOnlyCurrentActiveTenantAndOmitsContactFields() {
        Tenant tenant = new Tenant();
        tenant.setTenantId(8L);
        tenant.setTenantName("示例租户");
        tenant.setTenantCode("demo");
        tenant.setContactName("不应返回");
        tenant.setContactPhone("13800000000");
        tenant.setQuotaTokens(10000L);
        tenant.setUsedTokens(2300L);
        tenant.setStatus("0");
        ITenantService tenantService = service(ITenantService.class,
                (method, arguments) -> "selectTenantById".equals(method)
                        && Long.valueOf(8L).equals(arguments[0]) ? tenant : null);
        PlatformWorkflowDataProvider provider =
                new PlatformWorkflowDataProvider(tenantService);
        CallerContextHolder.set(new PlatformUserCallerContext(
                12L, 8L, "operator", true));
        try {
            var summary = provider.currentTenantUsage().orElseThrow();
            assertEquals(8L, summary.tenantId());
            assertEquals("示例租户", summary.tenantName());
            assertEquals("demo", summary.tenantCode());
            assertEquals(10000L, summary.quotaTokens());
            assertEquals(2300L, summary.usedTokens());
            assertEquals(5, summary.getClass().getRecordComponents().length);
        } finally {
            CallerContextHolder.clear();
        }
    }

    @Test
    void refusesTenantDataWithoutPlatformCaller() {
        ITenantService tenantService = service(ITenantService.class,
                (method, arguments) -> {
                    throw new AssertionError("不应查询租户数据");
                });
        PlatformWorkflowDataProvider provider =
                new PlatformWorkflowDataProvider(tenantService);

        CallerContextHolder.clear();

        assertTrue(provider.currentTenantUsage().isEmpty());
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
