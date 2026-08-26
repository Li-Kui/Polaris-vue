package com.polaris.ai.workflow.service;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.workflow.contract.WorkflowPermission;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/** 管理员、中台用户、API 密钥和服务主体共用的权限策略。 */
@Component("workflowAccess")
public class WorkflowAccessPolicy {

    public boolean canRead() {
        return can(WorkflowPermission.READ);
    }

    public boolean canEdit() {
        return can(WorkflowPermission.EDIT);
    }

    public boolean canPublish() {
        return can(WorkflowPermission.PUBLISH);
    }

    public boolean canExecute() {
        return can(WorkflowPermission.EXECUTE);
    }

    public boolean canCancel() {
        return can(WorkflowPermission.CANCEL);
    }

    public boolean canDebug() {
        return can(WorkflowPermission.DEBUG);
    }

    public boolean canApprove() {
        return can(WorkflowPermission.APPROVE);
    }

    public boolean canAdmin() {
        return can(WorkflowPermission.ADMIN);
    }

    public boolean can(String permission) {
        CallerContext caller = CallerUtils.getContext();
        if (caller.isSuperAdmin() || caller.hasPermission(WorkflowPermission.ADMIN)) {
            return true;
        }
        if (caller.hasPermission(permission)) {
            return true;
        }
        if (caller.isPlatformMode()) {
            if (caller.getUserId() == null) {
                return false;
            }
            if (caller.isTenantAdmin()) {
                return !WorkflowPermission.ADMIN.equals(permission);
            }
            return WorkflowPermission.READ.equals(permission)
                    || WorkflowPermission.EXECUTE.equals(permission)
                    || WorkflowPermission.CANCEL.equals(permission);
        }
        return currentAuthorities().contains(permission);
    }

    private Set<String> currentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
