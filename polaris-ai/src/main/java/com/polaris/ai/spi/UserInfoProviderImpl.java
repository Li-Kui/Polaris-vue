package com.polaris.ai.spi;

import com.polaris.ai.core.spi.UserInfoProvider;
import com.polaris.common.core.domain.entity.SysRole;
import com.polaris.common.core.domain.entity.SysUser;
import com.polaris.system.service.ISysRoleService;
import com.polaris.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 polaris-system 的用户信息 SPI 实现
 */
@Component
public class UserInfoProviderImpl implements UserInfoProvider {

    @Autowired(required = false)
    private ISysUserService userService;

    @Autowired(required = false)
    private ISysRoleService roleService;

    @Override
    public String getUserNameById(Long userId) {
        if (userService == null || userId == null) {
            return null;
        }
        try {
            SysUser user = userService.selectUserById(userId);
            return user != null ? user.getUserName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> getUserRoles(Long userId) {
        if (roleService == null || userId == null) {
            return Collections.emptyList();
        }
        try {
            List<SysRole> roles = roleService.selectRolesByUserId(userId);
            if (roles != null) {
                return roles.stream().map(SysRole::getRoleKey).collect(Collectors.toList());
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }
}
