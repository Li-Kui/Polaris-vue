package com.polaris.ai.safety.config;

import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.safety.provider.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

@Configuration
@EnableConfigurationProperties(ModerationProviderProperties.class)
public class ModerationProviderConfig {
    @Bean
    public ModerationProvider provider(ModerationProviderProperties properties) {
        if (!"aliyun".equals(normalize(properties.getType()))
                || blank(properties.getAccessKeyId())
                || blank(properties.getAccessKeySecret())
                || blank(properties.getEndpoint())
                || blank(properties.getService())
                || properties.getTimeoutMs() < 100
                || properties.getTimeoutMs() > 10_000
                || properties.getEstimatedCostPerCall() == null
                || properties.getEstimatedCostPerCall().signum() <= 0
                || costUnitsCeiling(properties.getEstimatedCostPerCall()).length() > 30
                || properties.getCircuitFailureThreshold() <= 0
                || properties.getCircuitOpenSeconds() <= 0) {
            return new NoopModerationProvider();
        }
        try {
            Config config = new Config()
                    .setAccessKeyId(properties.getAccessKeyId())
                    .setAccessKeySecret(properties.getAccessKeySecret())
                    .setEndpoint(properties.getEndpoint())
                    .setConnectTimeout(properties.getTimeoutMs())
                    .setReadTimeout(properties.getTimeoutMs());
            com.aliyun.green20220302.Client sdkClient =
                    new com.aliyun.green20220302.Client(config);
            return new AliyunModerationProvider(
                    new AliyunModerationProvider.SdkAliyunClient(sdkClient),
                    properties.getService());
        } catch (Exception invalidConfiguration) {
            return new NoopModerationProvider();
        }
    }

    @Bean
    public StringRedisTemplate moderationProviderStringRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public ProviderUsageGuard providerUsageGuard(
            @Qualifier("moderationProviderStringRedisTemplate")
            StringRedisTemplate redisTemplate,
            ModerationProviderProperties properties) {
        return new ProviderUsageGuard(
                new ProviderUsageGuard.RedisProviderStateStore(redisTemplate),
                new ObjectMapper(), properties, Clock.systemUTC());
    }

    @Bean
    public ProviderCircuitBreaker providerCircuitBreaker(
            ModerationProviderProperties properties) {
        int threshold = properties.getCircuitFailureThreshold() > 0
                ? properties.getCircuitFailureThreshold() : 5;
        long openSeconds = properties.getCircuitOpenSeconds() > 0
                ? properties.getCircuitOpenSeconds() : 60;
        return new ProviderCircuitBreaker(threshold,
                Duration.ofSeconds(openSeconds), Clock.systemUTC());
    }

    @Bean
    public ProviderReviewCoordinator providerReviewCoordinator(
            ModerationProvider provider,
            ProviderUsageGuard guard,
            ProviderCircuitBreaker circuitBreaker) {
        return new ProviderReviewCoordinator(provider, guard, circuitBreaker);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String costUnitsCeiling(java.math.BigDecimal amount) {
        try {
            return amount.setScale(8, RoundingMode.CEILING)
                    .movePointRight(8).toBigIntegerExact().toString();
        } catch (ArithmeticException invalid) {
            return "0".repeat(31);
        }
    }
}
