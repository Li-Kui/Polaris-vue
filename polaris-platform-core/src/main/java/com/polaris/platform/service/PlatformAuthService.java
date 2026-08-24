package com.polaris.platform.service;

import com.polaris.platform.auth.PlatformJwtUtils;
import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.domain.Tenant;
import com.polaris.platform.mapper.PlatformUserMapper;
import com.polaris.platform.mapper.TenantMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 中台用户认证服务
 */
@Service
public class PlatformAuthService {

    @Autowired
    private PlatformUserMapper userMapper;

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private PlatformJwtUtils jwtUtils;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String dummyPasswordHash = passwordEncoder.encode("polaris-platform-login-dummy-password");

    /**
     * 中台用户登录
     */
    public Map<String, Object> login(String tenantCode, String username, String password) {
        Tenant tenant = tenantMapper.selectByCode(tenantCode);
        if (tenant == null || !"0".equals(tenant.getStatus())) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw new PlatformAuthenticationException();
        }

        Long tenantId = tenant.getTenantId();
        PlatformUser user = userMapper.selectByUsername(tenantId, username);
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
        userMapper.updateLastLoginTime(user.getId());

        // 生成 Token
        String token = jwtUtils.generateToken(user.getId(), tenantId, username);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);
        return result;
    }

    public static class PlatformAuthenticationException extends RuntimeException {
        public PlatformAuthenticationException() {
            super("租户编码、用户名或密码错误");
        }
    }

    /**
     * 创建中台用户（密码加密）
     */
    public void createUser(PlatformUser user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
    }
}
