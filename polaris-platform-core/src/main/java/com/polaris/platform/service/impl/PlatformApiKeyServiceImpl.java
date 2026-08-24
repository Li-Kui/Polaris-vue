package com.polaris.platform.service.impl;

import com.polaris.platform.auth.ApiKeyDigestUtils;
import com.polaris.platform.domain.PlatformApiKey;
import com.polaris.platform.dto.PlatformApiKeyCreatedResponse;
import com.polaris.platform.mapper.PlatformApiKeyMapper;
import com.polaris.platform.service.IPlatformApiKeyService;
import com.polaris.platform.tenant.PlatformTenantGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 中台 API Key 服务层实现类
 *
 * @author polaris
 */
@Service
public class PlatformApiKeyServiceImpl implements IPlatformApiKeyService {

    @Autowired
    private PlatformApiKeyMapper platformApiKeyMapper;

    @Override
    public List<PlatformApiKey> selectApiKeyListByTenantId(Long tenantId) {
        if (!PlatformTenantGuard.belongsToCurrentTenant(tenantId)) {
            return List.of();
        }
        return platformApiKeyMapper.selectByTenantId(tenantId);
    }

    @Override
    public PlatformApiKey selectApiKeyById(Long id) {
        PlatformApiKey apiKey = platformApiKeyMapper.selectById(id);
        return apiKey != null && PlatformTenantGuard.belongsToCurrentTenant(apiKey.getTenantId()) ? apiKey : null;
    }

    /**
     * 创建 API Key，自动生成 sk-xxx 格式的密钥
     */
    @Override
    public PlatformApiKeyCreatedResponse createApiKey(PlatformApiKey apiKey) {
        String rawApiKey = "sk-" + UUID.randomUUID().toString().replace("-", "");
        apiKey.setTenantId(PlatformTenantGuard.requireTenantId());
        apiKey.setApiKeyHash(ApiKeyDigestUtils.digest(rawApiKey));
        apiKey.setKeyPrefix(ApiKeyDigestUtils.prefix(rawApiKey));
        platformApiKeyMapper.insert(apiKey);
        return new PlatformApiKeyCreatedResponse(
                apiKey.getId(), apiKey.getKeyName(), apiKey.getKeyPrefix(), rawApiKey);
    }

    @Override
    public int updateApiKey(PlatformApiKey apiKey) {
        PlatformApiKey current = selectApiKeyById(apiKey.getId());
        if (current == null) {
            return 0;
        }
        apiKey.setTenantId(current.getTenantId());
        return platformApiKeyMapper.update(apiKey);
    }

    @Override
    public int deleteApiKey(Long id) {
        if (selectApiKeyById(id) == null) {
            return 0;
        }
        return platformApiKeyMapper.deleteById(id);
    }
}
