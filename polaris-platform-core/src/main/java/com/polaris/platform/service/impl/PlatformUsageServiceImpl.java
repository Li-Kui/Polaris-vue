package com.polaris.platform.service.impl;

import com.polaris.platform.domain.Tenant;
import com.polaris.platform.dto.PlatformUsageResponse;
import com.polaris.platform.mapper.TenantMapper;
import com.polaris.platform.service.IPlatformUsageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 中台用量统计服务层实现类
 *
 * @author polaris
 */
@Service
public class PlatformUsageServiceImpl implements IPlatformUsageService {

    @Autowired
    private TenantMapper tenantMapper;

    @Override
    public PlatformUsageResponse selectTenantUsage(Long tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        PlatformUsageResponse usage = new PlatformUsageResponse();
        if (tenant != null) {
            usage.setTenantId(tenant.getTenantId());
            usage.setTenantName(tenant.getTenantName());
            usage.setQuotaTokens(tenant.getQuotaTokens());
            usage.setUsedTokens(tenant.getUsedTokens());
            long remaining = tenant.getQuotaTokens() == -1 ? -1
                    : Math.max(0, tenant.getQuotaTokens()
                            - (tenant.getUsedTokens() != null ? tenant.getUsedTokens() : 0));
            usage.setRemainingTokens(remaining);
        }
        return usage;
    }
}
