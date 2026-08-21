package com.polaris.platform.service;

import com.polaris.platform.auth.PlatformJwtUtils;
import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.mapper.PlatformUserMapper;
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
    private PlatformJwtUtils jwtUtils;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 中台用户登录
     */
    public Map<String, Object> login(Long tenantId, String username, String password) {
        PlatformUser user = userMapper.selectByUsername(tenantId, username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if ("1".equals(user.getStatus())) {
            throw new RuntimeException("用户已被停用");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
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

    /**
     * 创建中台用户（密码加密）
     */
    public void createUser(PlatformUser user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
    }
}
