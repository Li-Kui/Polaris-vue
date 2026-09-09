package com.polaris.platform.auth;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.workflow.spi.WorkflowPrincipalContextProvider;
import com.polaris.platform.domain.PlatformApiKey;
import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.domain.Tenant;
import com.polaris.platform.service.IPlatformApiKeyService;
import com.polaris.platform.service.IPlatformUserService;
import com.polaris.platform.service.ITenantService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;

/** 重新读取中台用户或 API Key，避免工作流沿用过期的请求身份。 */
@Component
public class PlatformWorkflowPrincipalContextProvider
        implements WorkflowPrincipalContextProvider {

    private final IPlatformUserService userService;
    private final IPlatformApiKeyService apiKeyService;
    private final ITenantService tenantService;

    public PlatformWorkflowPrincipalContextProvider(
            IPlatformUserService userService,
            IPlatformApiKeyService apiKeyService,
            ITenantService tenantService) {
        this.userService = userService;
        this.apiKeyService = apiKeyService;
        this.tenantService = tenantService;
    }

    @Override
    public boolean supports(String principalType) {
        return "PLATFORM_USER".equals(principalType)
                || "API_KEY".equals(principalType);
    }

    @Override
    public Optional<CallerContext> resolve(
            Long tenantId, String principalType, String principalId) {
        if (!activeTenant(tenantId)) return Optional.empty();
        long id;
        try {
            id = Long.parseLong(principalId);
        } catch (Exception e) {
            return Optional.empty();
        }
        if (id <= 0) return Optional.empty();
        if ("PLATFORM_USER".equals(principalType)) {
            PlatformUser user = userService.selectUserById(id);
            if (user == null || !tenantId.equals(user.getTenantId())
                    || !"0".equals(user.getStatus())) {
                return Optional.empty();
            }
            return Optional.of(new PlatformUserCallerContext(
                    user.getId(), tenantId, user.getUsername(),
                    "admin".equalsIgnoreCase(user.getRole())));
        }
        if ("API_KEY".equals(principalType)) {
            PlatformApiKey apiKey = apiKeyService.selectApiKeyById(id);
            if (apiKey == null || !tenantId.equals(apiKey.getTenantId())
                    || !"0".equals(apiKey.getStatus())
                    || apiKey.getExpireTime() != null
                    && !apiKey.getExpireTime().after(new Date())) {
                return Optional.empty();
            }
            return Optional.of(new ApiKeyCallerContext(
                    tenantId, apiKey.getId(), apiKey.getKeyName(), apiKey.getPermissions()));
        }
        return Optional.empty();
    }

    private boolean activeTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) return false;
        Tenant tenant = tenantService.selectTenantById(tenantId);
        return tenant != null && "0".equals(tenant.getStatus());
    }
}
