package com.polaris.ai.modelcenter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Model Center Runtime 基础设施配置。生产默认使用 Redis 集群级限流。 */
@Data
@Component
@ConfigurationProperties(prefix = "model-center.runtime")
public class ModelCenterRuntimeProperties {

    /** REDIS 用于多实例生产；LOCAL 只允许显式用于单节点开发。 */
    private LimiterMode limiterMode = LimiterMode.REDIS;

    /** Redis Key 前缀。 */
    private String limiterKeyPrefix = "model-center:runtime:";

    public enum LimiterMode {
        REDIS,
        LOCAL
    }
}
