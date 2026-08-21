package com.polaris.ai.core.spi;

import java.util.List;

/**
 * 用户信息提供者 SPI 接口。
 * 替代直接依赖 ISysUserService。
 */
public interface UserInfoProvider {

    /**
     * 根据用户ID获取用户名
     */
    String getUserNameById(Long userId);

    /**
     * 获取用户角色列表
     */
    List<String> getUserRoles(Long userId);
}
