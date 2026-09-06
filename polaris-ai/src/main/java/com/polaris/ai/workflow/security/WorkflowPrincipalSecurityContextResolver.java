package com.polaris.ai.workflow.security;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.workflow.spi.WorkflowNodeContext;
import com.polaris.ai.workflow.spi.WorkflowPrincipalContextProvider;
import com.polaris.common.constant.Constants;
import com.polaris.common.constant.UserConstants;
import com.polaris.common.core.domain.entity.SysUser;
import com.polaris.common.core.domain.model.LoginUser;
import com.polaris.system.service.ISysMenuService;
import com.polaris.system.service.ISysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 为脱离请求线程的工作流节点重新校验并恢复管理端用户身份。 */
@Component
public class WorkflowPrincipalSecurityContextResolver {

    private static final Logger log = LoggerFactory.getLogger(
            WorkflowPrincipalSecurityContextResolver.class);

    private final ISysUserService userService;
    private final ISysMenuService menuService;
    private final List<WorkflowPrincipalContextProvider> contextProviders;

    @Autowired
    public WorkflowPrincipalSecurityContextResolver(
            ISysUserService userService,
            ISysMenuService menuService,
            List<WorkflowPrincipalContextProvider> contextProviders) {
        this.userService = userService;
        this.menuService = menuService;
        this.contextProviders = contextProviders == null ? List.of() : List.copyOf(contextProviders);
    }

    public WorkflowPrincipalSecurityContextResolver(
            ISysUserService userService,
            ISysMenuService menuService) {
        this(userService, menuService, List.of());
    }

    public Optional<SecurityContext> resolve(WorkflowNodeContext context) {
        if (context == null) {
            return Optional.empty();
        }
        if (!"ADMIN".equals(context.principalType())) {
            return resolveExternalPrincipal(context);
        }
        if (context.tenantId() != null) return Optional.empty();
        try {
            long userId = Long.parseLong(context.principalId());
            if (userId <= 0) return Optional.empty();
            SysUser user = userService.selectUserById(userId);
            if (user == null
                    || !UserConstants.NORMAL.equals(user.getStatus())
                    || !"0".equals(user.getDelFlag())) {
                return Optional.empty();
            }
            Set<String> permissions = user.isAdmin()
                    ? Set.of(Constants.ALL_PERMISSION)
                    : menuService.selectMenuPermsByUserId(userId);
            LoginUser principal = new LoginUser(
                    user.getUserId(), user.getDeptId(), user, permissions);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            return Optional.of(securityContext);
        } catch (Exception e) {
            log.warn("无法恢复工作流执行主体 {}:{}，只读工具保持关闭",
                    context.principalType(), context.principalId(), e);
            return Optional.empty();
        }
    }

    private Optional<SecurityContext> resolveExternalPrincipal(WorkflowNodeContext context) {
        if (context.tenantId() == null || context.tenantId() <= 0) return Optional.empty();
        try {
            for (WorkflowPrincipalContextProvider provider : contextProviders) {
                if (!provider.supports(context.principalType())) continue;
                Optional<CallerContext> resolved = provider.resolve(
                        context.tenantId(), context.principalType(), context.principalId());
                if (resolved.isEmpty()) return Optional.empty();
                CallerContext principal = resolved.get();
                if (!String.valueOf(context.tenantId()).equals(principal.getTenantId())) {
                    return Optional.empty();
                }
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal, null, java.util.List.of());
                SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
                securityContext.setAuthentication(authentication);
                return Optional.of(securityContext);
            }
        } catch (Exception e) {
            log.warn("无法恢复工作流执行主体 {}:{}，只读工具保持关闭",
                    context.principalType(), context.principalId(), e);
        }
        return Optional.empty();
    }
}
