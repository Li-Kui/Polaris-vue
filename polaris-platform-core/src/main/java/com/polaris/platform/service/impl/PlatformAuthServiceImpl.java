package com.polaris.platform.service.impl;

import com.polaris.platform.auth.PlatformJwtUtils;
import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.domain.Tenant;
import com.polaris.platform.dto.PlatformLoginResponse;
import com.polaris.platform.dto.PlatformUserResponse;
import com.polaris.platform.mapper.PlatformUserMapper;
import com.polaris.platform.mapper.TenantMapper;
import com.polaris.platform.service.IPlatformAuthService;
import com.polaris.platform.service.PlatformAuthenticationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 中台用户认证服务层实现类
 *
 * @author polaris
 */
@Service
public class PlatformAuthServiceImpl implements IPlatformAuthService {

    @Autowired
    private PlatformUserMapper platformUserMapper;

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private PlatformJwtUtils jwtUtils;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String dummyPasswordHash = passwordEncoder.encode("polaris-platform-login-dummy-password");

    /**
     * 中台用户登录
     */
    @Override
    public PlatformLoginResponse login(String tenantCode, String username, String password) {
        Tenant tenant = tenantMapper.selectByCode(tenantCode);
        if (tenant == null || !"0".equals(tenant.getStatus())) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw new PlatformAuthenticationException();
        }

        Long tenantId = tenant.getTenantId();
        PlatformUser user = platformUserMapper.selectByUsername(tenantId, username);
        String storedPassword = user != null && user.getPassword() != null
                ? user.getPassword() : dummyPasswordHash;
        boolean passwordMatched;
        try {
            passwordMatched = passwordEncoder.matches(password, storedPassword);
        } catch (IllegalArgumentException e) {
            passwordMatched = false;
        }
        if (user == null || !"0".equals(user.getStatus()) || !passwordMatched) {
            throw new PlatformAuthenticationException();
        }

        // 更新最后登录时间
        platformUserMapper.updateLastLoginTime(user.getId());

        // 生成 Token
        String token = jwtUtils.generateToken(user.getId(), tenantId, username);

        return new PlatformLoginResponse(token, PlatformUserResponse.from(user));
    }

    /**
     * 创建中台用户（密码加密）
     */
    @Override
    public int createPlatformUser(PlatformUser user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return platformUserMapper.insert(user);
    }
}
