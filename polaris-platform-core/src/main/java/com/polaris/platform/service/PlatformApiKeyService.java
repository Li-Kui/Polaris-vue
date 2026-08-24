package com.polaris.platform.service;

import com.polaris.platform.auth.ApiKeyDigestUtils;
import com.polaris.platform.domain.PlatformApiKey;
import com.polaris.platform.dto.PlatformApiKeyCreatedResponse;
import com.polaris.platform.mapper.PlatformApiKeyMapper;
import com.polaris.platform.tenant.PlatformTenantGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * API Key 管理服务
 */
@Service
public class PlatformApiKeyService {

    @Autowired
    private PlatformApiKeyMapper apiKeyMapper;

    public List<PlatformApiKey> listByTenantId(Long tenantId) {
        if (!PlatformTenantGuard.belongsToCurrentTenant(tenantId)) {
            return List.of();
        }
        return apiKeyMapper.selectByTenantId(tenantId);
    }

    public PlatformApiKey getById(Long id) {
        PlatformApiKey apiKey = apiKeyMapper.selectById(id);
        return apiKey != null && PlatformTenantGuard.belongsToCurrentTenant(apiKey.getTenantId()) ? apiKey : null;
    }

    /**
     * 创建 API Key，自动生成 sk-xxx 格式的密钥
     */
    public PlatformApiKeyCreatedResponse create(PlatformApiKey apiKey) {
        String rawApiKey = "sk-" + UUID.randomUUID().toString().replace("-", "");
        apiKey.setTenantId(PlatformTenantGuard.requireTenantId());
        apiKey.setApiKeyHash(ApiKeyDigestUtils.digest(rawApiKey));
        apiKey.setKeyPrefix(ApiKeyDigestUtils.prefix(rawApiKey));
        apiKeyMapper.insert(apiKey);
        return new PlatformApiKeyCreatedResponse(
                apiKey.getId(), apiKey.getKeyName(), apiKey.getKeyPrefix(), rawApiKey);
    }

    public int update(PlatformApiKey apiKey) {
        PlatformApiKey current = getById(apiKey.getId());
        if (current == null) {
            return 0;
        }
        apiKey.setTenantId(current.getTenantId());
        return apiKeyMapper.update(apiKey);
    }

    public int delete(Long id) {
        if (getById(id) == null) {
            return 0;
        }
        return apiKeyMapper.deleteById(id);
    }
}
