package com.polaris.platform.auth;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.workflow.spi.WorkflowPlatformDataProvider;
import com.polaris.platform.domain.Tenant;
import com.polaris.platform.service.ITenantService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 从当前中台身份读取最小化的租户概览。 */
@Component
public class PlatformWorkflowDataProvider implements WorkflowPlatformDataProvider {

    private final ITenantService tenantService;

    public PlatformWorkflowDataProvider(ITenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Override
    public Optional<TenantUsageSummary> currentTenantUsage() {
        CallerContext caller = CallerContextHolder.get();
        if (caller == null || !caller.isPlatformMode()) return Optional.empty();
        Long tenantId = parseTenantId(caller.getTenantId());
        if (tenantId == null) return Optional.empty();
        Tenant tenant = tenantService.selectTenantById(tenantId);
        if (tenant == null || !tenantId.equals(tenant.getTenantId())
                || !"0".equals(tenant.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(new TenantUsageSummary(
                tenant.getTenantId(), tenant.getTenantName(), tenant.getTenantCode(),
                tenant.getQuotaTokens(), tenant.getUsedTokens()));
    }

    private Long parseTenantId(String value) {
        try {
            long tenantId = Long.parseLong(value);
            return tenantId > 0 ? tenantId : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
