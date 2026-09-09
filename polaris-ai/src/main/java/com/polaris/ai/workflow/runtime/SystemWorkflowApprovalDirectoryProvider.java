package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.spi.WorkflowApprovalDirectoryEntry;
import com.polaris.ai.workflow.spi.WorkflowApprovalDirectoryProvider;
import com.polaris.ai.workflow.spi.WorkflowApprovalPrincipal;
import com.polaris.ai.workflow.spi.WorkflowApprovalTarget;
import com.polaris.common.core.domain.entity.SysDept;
import com.polaris.common.core.domain.entity.SysUser;
import com.polaris.system.mapper.SysDeptMapper;
import com.polaris.system.mapper.SysRoleMapper;
import com.polaris.system.mapper.SysUserMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 解析管理端系统用户、角色和部门目标。 */
@Component
@Order(10)
public class SystemWorkflowApprovalDirectoryProvider
        implements WorkflowApprovalDirectoryProvider {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;

    public SystemWorkflowApprovalDirectoryProvider(
            SysUserMapper userMapper,
            SysRoleMapper roleMapper,
            SysDeptMapper deptMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.deptMapper = deptMapper;
    }

    @Override
    public boolean supports(Long tenantId) {
        return tenantId == null;
    }

    @Override
    public List<WorkflowApprovalPrincipal> resolve(
            Long tenantId,
            List<WorkflowApprovalTarget> targets) {
        List<WorkflowApprovalPrincipal> result = new ArrayList<>();
        for (WorkflowApprovalTarget target : targets) {
            switch (target.type().toUpperCase(java.util.Locale.ROOT)) {
                case "USER" -> target.ids().forEach(id -> addUser(result,
                        parseId(id, "用户"), "USER:" + id));
                case "ROLE" -> target.ids().forEach(id -> {
                    SysUser filter = new SysUser();
                    filter.setRoleId(parseId(id, "角色"));
                    filter.setStatus("0");
                    userMapper.selectAllocatedList(filter).stream()
                            .filter(SystemWorkflowApprovalDirectoryProvider::active)
                            .map(user -> principal(user, "ROLE:" + id))
                            .forEach(result::add);
                });
                case "DEPARTMENT" -> target.ids().forEach(id -> {
                    Long departmentId = parseId(id, "部门");
                    SysUser filter = new SysUser();
                    filter.setDeptId(departmentId);
                    filter.setStatus("0");
                    userMapper.selectUserList(filter).stream()
                            .filter(SystemWorkflowApprovalDirectoryProvider::active)
                            .filter(user -> target.includeChildren()
                                    || departmentId.equals(user.getDeptId()))
                            .map(user -> principal(user, "DEPARTMENT:" + id))
                            .forEach(result::add);
                });
                default -> throw new IllegalArgumentException(
                        "不支持的审批目标类型：" + target.type());
            }
        }
        return result;
    }

    @Override
    public List<WorkflowApprovalDirectoryEntry> list(Long tenantId, String keyword) {
        String query = keyword == null ? "" : keyword.toLowerCase(java.util.Locale.ROOT);
        List<WorkflowApprovalDirectoryEntry> result = new ArrayList<>();
        SysUser userFilter = new SysUser();
        userFilter.setStatus("0");
        userMapper.selectUserList(userFilter).stream()
                .filter(SystemWorkflowApprovalDirectoryProvider::active)
                .filter(user -> matches(query, user.getUserName(), user.getNickName()))
                .limit(500)
                .map(user -> new WorkflowApprovalDirectoryEntry(
                        "USER", String.valueOf(user.getUserId()),
                        displayName(user), user.getUserName(), true))
                .forEach(result::add);
        roleMapper.selectRoleAll().stream()
                .filter(role -> matches(query, role.getRoleName(), role.getRoleKey()))
                .limit(200)
                .map(role -> new WorkflowApprovalDirectoryEntry(
                        "ROLE", String.valueOf(role.getRoleId()), role.getRoleName(),
                        role.getRoleKey(), "0".equals(role.getStatus())))
                .forEach(result::add);
        deptMapper.selectDeptList(new SysDept()).stream()
                .filter(dept -> matches(query, dept.getDeptName(), String.valueOf(dept.getDeptId())))
                .limit(500)
                .map(dept -> new WorkflowApprovalDirectoryEntry(
                        "DEPARTMENT", String.valueOf(dept.getDeptId()), dept.getDeptName(),
                        "部门", "0".equals(dept.getStatus())))
                .forEach(result::add);
        return result;
    }

    private void addUser(List<WorkflowApprovalPrincipal> result, Long userId, String source) {
        SysUser user = userMapper.selectUserById(userId);
        if (active(user)) {
            result.add(principal(user, source));
        }
    }

    private static WorkflowApprovalPrincipal principal(SysUser user, String source) {
        SysDept department = user.getDept();
        return new WorkflowApprovalPrincipal(
                String.valueOf(user.getUserId()),
                user.getUserName(),
                displayName(user),
                user.getDeptId() == null ? null : String.valueOf(user.getDeptId()),
                department == null ? null : department.getDeptName(),
                List.of(source));
    }

    private static String displayName(SysUser user) {
        return user.getNickName() == null || user.getNickName().isBlank()
                ? user.getUserName() : user.getNickName();
    }

    private static boolean matches(String query, String... values) {
        if (query == null || query.isBlank()) return true;
        for (String value : values) {
            if (value != null && value.toLowerCase(java.util.Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private static boolean active(SysUser user) {
        return user != null
                && user.getUserId() != null
                && "0".equals(user.getStatus())
                && (user.getDelFlag() == null || "0".equals(user.getDelFlag()));
    }

    private static Long parseId(String value, String label) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " ID 无效：" + value, ex);
        }
    }
}
