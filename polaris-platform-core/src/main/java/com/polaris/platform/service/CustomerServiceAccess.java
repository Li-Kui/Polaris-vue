package com.polaris.platform.service;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.auth.PlatformUserCallerContext;
import com.polaris.platform.mapper.PlatformUserMapper;
import com.polaris.platform.tenant.PlatformTenantGuard;
import org.springframework.stereotype.Component;

/** 配置及工作流仅允许有效租户管理员；会话运行另接受已校验的专属访客主体。 */
@Component
public class CustomerServiceAccess {
    private final PlatformUserMapper users;

    public CustomerServiceAccess(PlatformUserMapper users) {
        this.users = users;
    }

    public Actor requireAdmin() {
        var caller = CallerContextHolder.get();
        if (!(caller instanceof PlatformUserCallerContext) || caller.getUserId() == null) {
            throw new ServiceException("ACCESS_DENIED: 请使用租户管理员账号", 403);
        }
        Long tenantId = PlatformTenantGuard.requireTenantId();
        var user = users.selectAuthUser(caller.getUserId(), tenantId, caller.getUsername());
        if (user == null || !tenantId.equals(user.getTenantId()) || !"0".equals(user.getStatus())
                || !"admin".equals(user.getRole())) {
            throw new ServiceException("ACCESS_DENIED: 当前账号没有客服配置或预览权限", 403);
        }
        return new Actor(tenantId, user.getId(), user.getUsername());
    }

    /** 会话运行仅额外接受已校验的访客主体，配置／工作流等仍调用 requireAdmin。 */
    public Actor requireConversationActor() {
        if (CallerContextHolder.get() instanceof com.polaris.ai.core.context.CustomerServiceVisitorContext visitor) {
            return new Actor(visitor.tenantId(), 0L, visitor.getUsername());
        }
        return requireAdmin();
    }

    public record Actor(Long tenantId, Long userId, String username) { }
}
