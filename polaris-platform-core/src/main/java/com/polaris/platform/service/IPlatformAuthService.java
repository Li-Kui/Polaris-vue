package com.polaris.platform.service;

import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.dto.PlatformLoginResponse;

/**
 * 中台用户认证服务层接口
 *
 * @author polaris
 */
public interface IPlatformAuthService {

    PlatformLoginResponse login(String tenantCode, String username, String password);

    int createPlatformUser(PlatformUser user);
}
