package com.polaris.platform.service;

import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.domain.Tenant;
import com.polaris.platform.mapper.TenantMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 租户管理服务
 */
@Service
public class TenantService {

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private PlatformAuthService authService;

    public List<Tenant> list(Tenant query) {
        return tenantMapper.selectList(query);
    }

    public Tenant getById(Long tenantId) {
        return tenantMapper.selectById(tenantId);
    }

    public Tenant getByCode(String tenantCode) {
        return tenantMapper.selectByCode(tenantCode);
    }

    /**
     * 创建租户，同时创建默认管理员账号
     */
    @Transactional
    public Tenant create(Tenant tenant, String adminUsername, String adminPassword) {
        tenantMapper.insert(tenant);

        // 创建默认管理员
        PlatformUser admin = new PlatformUser();
        admin.setTenantId(tenant.getTenantId());
        admin.setUsername(adminUsername);
        admin.setPassword(adminPassword);
        admin.setNickname("管理员");
        admin.setRole("admin");
        admin.setStatus("0");
        authService.createUser(admin);

        return tenant;
    }

    public int update(Tenant tenant) {
        return tenantMapper.update(tenant);
    }

    public int delete(Long tenantId) {
        return tenantMapper.deleteById(tenantId);
    }
}
