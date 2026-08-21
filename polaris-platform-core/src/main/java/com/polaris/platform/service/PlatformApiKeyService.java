package com.polaris.platform.service;

import com.polaris.platform.domain.PlatformApiKey;
import com.polaris.platform.mapper.PlatformApiKeyMapper;
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
        return apiKeyMapper.selectByTenantId(tenantId);
    }

    public PlatformApiKey getById(Long id) {
        return apiKeyMapper.selectById(id);
    }

    /**
     * 创建 API Key，自动生成 sk-xxx 格式的密钥
     */
    public PlatformApiKey create(PlatformApiKey apiKey) {
        apiKey.setApiKey("sk-" + UUID.randomUUID().toString().replace("-", ""));
        apiKeyMapper.insert(apiKey);
        return apiKey;
    }

    public int update(PlatformApiKey apiKey) {
        return apiKeyMapper.update(apiKey);
    }

    public int delete(Long id) {
        return apiKeyMapper.deleteById(id);
    }
}
