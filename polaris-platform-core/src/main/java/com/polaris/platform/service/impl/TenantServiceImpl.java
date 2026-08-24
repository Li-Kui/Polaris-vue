package com.polaris.platform.service.impl;

import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.domain.Tenant;
import com.polaris.platform.mapper.TenantMapper;
import com.polaris.platform.service.IPlatformAuthService;
import com.polaris.platform.service.ITenantService;
import com.polaris.platform.service.TokenQuotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 中台租户服务层实现类
 *
 * @author polaris
 */
@Service
public class TenantServiceImpl implements ITenantService {

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private IPlatformAuthService platformAuthService;

    @Autowired
    private TokenQuotaService tokenQuotaService;

    @Override
    public List<Tenant> selectTenantList(Tenant tenant) {
        return tenantMapper.selectList(tenant);
    }

    @Override
    public Tenant selectTenantById(Long tenantId) {
        return tenantMapper.selectById(tenantId);
    }

    @Override
    public Tenant selectTenantByCode(String tenantCode) {
        return tenantMapper.selectByCode(tenantCode);
    }

    /**
     * 创建租户，同时创建默认管理员账号
     */
    @Transactional
    @Override
    public Tenant createTenant(Tenant tenant, String adminUsername, String adminPassword) {
        tenantMapper.insert(tenant);

        // 创建默认管理员
        PlatformUser admin = new PlatformUser();
        admin.setTenantId(tenant.getTenantId());
        admin.setUsername(adminUsername);
        admin.setPassword(adminPassword);
        admin.setNickname("管理员");
        admin.setRole("admin");
        admin.setStatus("0");
        platformAuthService.createPlatformUser(admin);

        tokenQuotaService.refresh(tenant);

        return tenant;
    }

    @Override
    public int updateTenant(Tenant tenant) {
        int rows = tenantMapper.update(tenant);
        if (rows > 0) {
            tokenQuotaService.refresh(tenantMapper.selectById(tenant.getTenantId()));
        }
        return rows;
    }

    @Override
    public int deleteTenant(Long tenantId) {
        int rows = tenantMapper.deleteById(tenantId);
        if (rows > 0) {
            tokenQuotaService.evict(tenantId);
        }
        return rows;
    }
}
