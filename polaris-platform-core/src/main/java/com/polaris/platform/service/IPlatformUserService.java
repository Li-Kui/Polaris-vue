package com.polaris.platform.service;

import com.polaris.platform.domain.PlatformUser;

import java.util.List;

/**
 * 中台租户用户服务层接口
 *
 * @author polaris
 */
public interface IPlatformUserService {

    List<PlatformUser> selectUserListByTenantId(Long tenantId);

    PlatformUser selectUserById(Long id);

    int insertUser(PlatformUser user);

    int updateUser(PlatformUser user);

    int deleteUserById(Long id);
}
