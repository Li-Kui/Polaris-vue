package com.polaris.ai.workflow.service;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.workflow.application.WorkflowResourceCatalogApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowResourceOption;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.registry.WorkflowResourceRegistry;
import com.polaris.ai.workflow.spi.WorkflowResourceCatalogRequest;
import com.polaris.ai.workflow.spi.WorkflowResourceProvider;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 汇总现有业务资源并输出不含凭据的工作流选择目录。 */
@Service
public class WorkflowResourceCatalogService
        implements WorkflowResourceCatalogApplicationFacade {

    private static final Set<String> ENVIRONMENTS = Set.of("DEV", "TEST", "PROD");

    private final WorkflowResourceRegistry resourceRegistry;
    private final WorkflowProperties properties;

    public WorkflowResourceCatalogService(
            WorkflowResourceRegistry resourceRegistry,
            WorkflowProperties properties) {
        this.resourceRegistry = resourceRegistry;
        this.properties = properties;
    }

    @Override
    public List<WorkflowResourceOption> list(
            String kind, String environment) {
        requireEnabled();
        String normalizedKind = normalizeKind(kind);
        WorkflowResourceProvider provider = resourceRegistry.find(normalizedKind)
                .orElseThrow(() -> new ServiceException("资源类型尚未注册: " + normalizedKind));
        CallerContext caller = CallerUtils.getContext();
        Long tenantId = currentTenantId();
        WorkflowResourceCatalogRequest request = new WorkflowResourceCatalogRequest(
                tenantId,
                normalizeEnvironment(environment),
                principalType(caller),
                String.valueOf(CallerUtils.getUserId()),
                caller.getDeptId(),
                caller.isSuperAdmin());
        List<WorkflowResourceOption> result = provider.listAvailable(request);
        if (result == null) return List.of();
        return result.stream()
                .sorted(Comparator.comparing(
                                (WorkflowResourceOption option) -> !option.available())
                        .thenComparing(WorkflowResourceOption::shared)
                        .thenComparing(WorkflowResourceOption::name,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private Long currentTenantId() {
        if (CallerUtils.isPlatformMode()) {
            return parseTenantId(CallerUtils.getTenantId());
        }
        return null;
    }

    private String normalizeKind(String value) {
        String result = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new ServiceException("资源类型格式无效");
        }
        return result;
    }

    private String normalizeEnvironment(String value) {
        String result = value == null || value.isBlank()
                ? "PROD" : value.trim().toUpperCase(Locale.ROOT);
        if (!ENVIRONMENTS.contains(result)) {
            throw new ServiceException("资源环境只能是DEV、TEST或PROD");
        }
        return result;
    }

    private Long parseTenantId(String value) {
        try {
            long tenantId = Long.parseLong(value);
            if (tenantId <= 0) throw new NumberFormatException();
            return tenantId;
        } catch (Exception e) {
            throw new ServiceException("租户ID格式错误");
        }
    }

    private String principalType(CallerContext caller) {
        if (CallerUtils.getUsername().startsWith("apikey:")) return "API_KEY";
        return caller.isPlatformMode() ? "PLATFORM_USER" : "ADMIN";
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ServiceException("工作流尚未启用");
        }
    }
}
