package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.spi.WorkflowApprovalDirectoryEntry;
import com.polaris.ai.workflow.spi.WorkflowApprovalDirectoryProvider;
import com.polaris.ai.workflow.spi.WorkflowApprovalPrincipal;
import com.polaris.ai.workflow.spi.WorkflowApprovalTarget;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 在管理端与租户端用户目录之间选择正确的审批人员解析器。 */
@Component
public class WorkflowApprovalDirectoryResolver {

    private final List<WorkflowApprovalDirectoryProvider> providers;

    public WorkflowApprovalDirectoryResolver(List<WorkflowApprovalDirectoryProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public List<WorkflowApprovalPrincipal> resolve(
            Long tenantId,
            List<WorkflowApprovalTarget> targets) {
        WorkflowApprovalDirectoryProvider provider = provider(tenantId);
        Map<String, WorkflowApprovalPrincipal> unique = new LinkedHashMap<>();
        for (WorkflowApprovalPrincipal principal : provider.resolve(tenantId, targets)) {
            if (principal != null && principal.userId() != null) {
                unique.merge(principal.userId(), principal,
                        WorkflowApprovalDirectoryResolver::mergeSources);
            }
        }
        return List.copyOf(unique.values());
    }

    public List<WorkflowApprovalDirectoryEntry> list(Long tenantId, String keyword) {
        return provider(tenantId).list(
                tenantId, keyword == null ? "" : keyword.trim());
    }

    private WorkflowApprovalDirectoryProvider provider(Long tenantId) {
        return providers.stream()
                .filter(candidate -> candidate.supports(tenantId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "找不到当前工作流所属范围的审批人员目录"));
    }

    private static WorkflowApprovalPrincipal mergeSources(
            WorkflowApprovalPrincipal first,
            WorkflowApprovalPrincipal second) {
        List<String> sources = java.util.stream.Stream
                .concat(first.sources().stream(), second.sources().stream())
                .distinct()
                .toList();
        return new WorkflowApprovalPrincipal(
                first.userId(), first.username(), first.displayName(),
                first.departmentId(), first.departmentName(), sources);
    }
}
