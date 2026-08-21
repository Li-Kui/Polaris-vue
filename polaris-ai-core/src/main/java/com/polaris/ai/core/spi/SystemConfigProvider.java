package com.polaris.ai.core.spi;

/**
 * 系统配置提供者 SPI 接口。
 * 替代直接依赖 ISysConfigService，实现 AI 引擎层与系统管理层解耦。
 */
public interface SystemConfigProvider {

    /**
     * 根据配置键获取配置值
     *
     * @param configKey 配置键
     * @return 配置值，不存在时返回 null
     */
    String getConfigValue(String configKey);

    /**
     * 根据配置键获取配置值，不存在时返回默认值
     */
    default String getConfigValue(String configKey, String defaultValue) {
        String value = getConfigValue(configKey);
        return value != null ? value : defaultValue;
    }
}
