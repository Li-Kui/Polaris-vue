package com.polaris.platform.service;

import com.polaris.platform.domain.Tenant;
import com.polaris.platform.mapper.TenantMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PlatformUsageService {

    @Autowired
    private TenantMapper tenantMapper;

    public Map<String, Object> getTenantUsage(Long tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        Map<String, Object> usage = new HashMap<>();
        if (tenant != null) {
            usage.put("tenantId", tenant.getTenantId());
            usage.put("tenantName", tenant.getTenantName());
            usage.put("quotaTokens", tenant.getQuotaTokens());
            usage.put("usedTokens", tenant.getUsedTokens());
            long remaining = tenant.getQuotaTokens() == -1 ? -1 : Math.max(0, tenant.getQuotaTokens() - (tenant.getUsedTokens() != null ? tenant.getUsedTokens() : 0));
            usage.put("remainingTokens", remaining);
        }
        return usage;
    }
}
