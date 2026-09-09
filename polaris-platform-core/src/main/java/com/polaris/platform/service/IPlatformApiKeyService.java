package com.polaris.platform.service;

import com.polaris.platform.domain.PlatformApiKey;
import com.polaris.platform.dto.PlatformApiKeyCreatedResponse;

import java.util.List;

/**
 * 中台 API Key 服务层接口
 *
 * @author polaris
 */
public interface IPlatformApiKeyService {

    List<PlatformApiKey> selectApiKeyListByTenantId(Long tenantId);

    PlatformApiKey selectApiKeyById(Long id);

    PlatformApiKeyCreatedResponse createApiKey(PlatformApiKey apiKey);

    int updateApiKey(PlatformApiKey apiKey);

    int deleteApiKey(Long id);
}
