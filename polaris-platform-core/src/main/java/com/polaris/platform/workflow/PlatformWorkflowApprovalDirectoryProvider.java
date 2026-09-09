package com.polaris.platform.workflow;

import com.polaris.ai.workflow.spi.WorkflowApprovalDirectoryEntry;
import com.polaris.ai.workflow.spi.WorkflowApprovalDirectoryProvider;
import com.polaris.ai.workflow.spi.WorkflowApprovalPrincipal;
import com.polaris.ai.workflow.spi.WorkflowApprovalTarget;
import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.mapper.PlatformUserMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 解析租户端用户及租户角色目标；租户端目前没有部门目录。 */
@Component
@Order(20)
public class PlatformWorkflowApprovalDirectoryProvider
        implements WorkflowApprovalDirectoryProvider {

    private final PlatformUserMapper userMapper;

    public PlatformWorkflowApprovalDirectoryProvider(PlatformUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean supports(Long tenantId) {
        return tenantId != null;
    }

    @Override
    public List<WorkflowApprovalPrincipal> resolve(
            Long tenantId,
            List<WorkflowApprovalTarget> targets) {
        List<PlatformUser> users = userMapper.selectByTenantId(tenantId).stream()
                .filter(user -> "0".equals(user.getStatus()))
                .toList();
        List<WorkflowApprovalPrincipal> result = new ArrayList<>();
        for (WorkflowApprovalTarget target : targets) {
            String type = target.type().toUpperCase(Locale.ROOT);
            if ("DEPARTMENT".equals(type)) {
                throw new IllegalArgumentException("租户端尚未配置部门目录，不能使用部门审批目标");
            }
            if ("USER".equals(type)) {
                for (String id : target.ids()) {
                    users.stream()
                            .filter(user -> String.valueOf(user.getId()).equals(id))
                            .map(user -> principal(user, "USER:" + id))
                            .forEach(result::add);
                }
            } else if ("ROLE".equals(type)) {
                for (String role : target.ids()) {
                    users.stream()
                            .filter(user -> role.equalsIgnoreCase(user.getRole()))
                            .map(user -> principal(user, "ROLE:" + role))
                            .forEach(result::add);
                }
            } else {
                throw new IllegalArgumentException("不支持的审批目标类型：" + target.type());
            }
        }
        return result;
    }

    @Override
    public List<WorkflowApprovalDirectoryEntry> list(Long tenantId, String keyword) {
        String query = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        List<WorkflowApprovalDirectoryEntry> result = new ArrayList<>();
        userMapper.selectByTenantId(tenantId).stream()
                .filter(user -> matches(query, user.getUsername(), user.getNickname()))
                .limit(500)
                .map(user -> new WorkflowApprovalDirectoryEntry(
                        "USER", String.valueOf(user.getId()), displayName(user),
                        user.getUsername(), "0".equals(user.getStatus())))
                .forEach(result::add);
        result.add(new WorkflowApprovalDirectoryEntry(
                "ROLE", "admin", "租户管理员", "所有租户管理员", true));
        result.add(new WorkflowApprovalDirectoryEntry(
                "ROLE", "member", "租户成员", "所有正常租户成员", true));
        return result;
    }

    private static WorkflowApprovalPrincipal principal(PlatformUser user, String source) {
        return new WorkflowApprovalPrincipal(
                String.valueOf(user.getId()), user.getUsername(),
                displayName(user),
                null, null, List.of(source));
    }

    private static String displayName(PlatformUser user) {
        return user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername() : user.getNickname();
    }

    private static boolean matches(String query, String... values) {
        if (query == null || query.isBlank()) return true;
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }
}
