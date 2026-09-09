package com.polaris.ai.spi;

import com.polaris.ai.core.spi.SystemConfigProvider;
import com.polaris.system.service.ISysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 基于 polaris-system 的系统配置 SPI 实现
 */
@Component
public class SystemConfigProviderImpl implements SystemConfigProvider {

    @Autowired(required = false)
    private ISysConfigService configService;

    @Override
    public String getConfigValue(String configKey) {
        if (configService == null || configKey == null) {
            return null;
        }
        try {
            return configService.selectConfigByKey(configKey);
        } catch (Exception e) {
            return null;
        }
    }
}
