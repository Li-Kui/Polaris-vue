package com.polaris.platform.service;

import com.polaris.platform.dto.PlatformUsageResponse;

/**
 * 中台用量统计服务层接口
 *
 * @author polaris
 */
public interface IPlatformUsageService {

    PlatformUsageResponse selectTenantUsage(Long tenantId);
}
