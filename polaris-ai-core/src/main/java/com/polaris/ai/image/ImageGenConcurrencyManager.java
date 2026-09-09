package com.polaris.ai.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * 绘图厂商 API 动态并发信号量管理器
 * 防止同时发送过多的在途渲染请求触发厂商 HTTP 429 (RateQuota Exceeded) 限流。
 * 支持根据厂商/模型配置动态初始化并发许可，并支持 429 触发时的智能自适应降级。
 *
 * @author polaris
 */
@Slf4j
@Component
public class ImageGenConcurrencyManager {

    /** 信号量 Map: key 为 modelConfigId */
    private final Map<Long, Semaphore> semaphoreMap = new ConcurrentHashMap<>();
    /** 当前并发上限 Map: key 为 modelConfigId */
    private final Map<Long, Integer> limitMap = new ConcurrentHashMap<>();

    /**
     * 获取并发许可（支持从 AiModelConfig 动态提取自定义并发上限）
     *
     * @param config 模型配置
     */
    public void acquire(com.polaris.ai.domain.AiModelConfig config) {
        if (config == null) {
            acquire(-1L, "", null);
            return;
        }
        acquire(config.getId(), config.getProvider(), config.getMaxConcurrency());
    }

    /**
     * 获取并发许可（阻塞排队等待，直到获得许可）
     *
     * @param configId 模型配置 ID
     * @param provider 厂商标识 (dashscope / doubao / openai ...)
     * @param customMaxConcurrency 自定义最大并发上限（可为空）
     */
    public void acquire(Long configId, String provider, Integer customMaxConcurrency) {
        if (configId == null) {
            configId = -1L;
        }
        Semaphore semaphore = getOrCreateSemaphore(configId, provider, customMaxConcurrency);
        try {
            log.debug(">>> [ConcurrencyManager] 尝试获取信号量许可, configId={}, provider={}, availablePermits={}",
                    configId, provider, semaphore.availablePermits());
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(">>> [ConcurrencyManager] 等待并发许可被中断, configId={}", configId);
        }
    }

    /**
     * 释放并发许可
     *
     * @param configId 模型配置 ID
     */
    public void release(Long configId) {
        if (configId == null) {
            configId = -1L;
        }
        Semaphore semaphore = semaphoreMap.get(configId);
        if (semaphore != null) {
            semaphore.release();
            log.debug(">>> [ConcurrencyManager] 释放信号量许可, configId={}, availablePermits={}",
                    configId, semaphore.availablePermits());
        }
    }

    /**
     * 当触发厂商 HTTP 429 (RateLimit Exceeded) 时，智能自适应下调并发上限
     *
     * @param configId 模型配置 ID
     */
    public void onRateLimitExceeded(Long configId) {
        if (configId == null) {
            configId = -1L;
        }
        Integer currentLimit = limitMap.get(configId);
        if (currentLimit != null && currentLimit > 1) {
            int newLimit = Math.max(1, currentLimit - 1);
            limitMap.put(configId, newLimit);
            log.warn(">>> [ConcurrencyManager] 检测到厂商 429 限流, 智能自适应下调并发上限: configId={}, {} -> {}",
                    configId, currentLimit, newLimit);
        }
    }

    private Semaphore getOrCreateSemaphore(Long configId, String provider, Integer customMaxConcurrency) {
        return semaphoreMap.computeIfAbsent(configId, id -> {
            int limit = (customMaxConcurrency != null && customMaxConcurrency > 0)
                    ? customMaxConcurrency : getDefaultLimitByProvider(provider);
            limitMap.put(id, limit);
            log.info(">>> [ConcurrencyManager] 初始化模型配置信号量, configId={}, provider={}, maxConcurrency={}",
                    id, provider, limit);
            return new Semaphore(limit, true); // 公平锁，先进先出排队
        });
    }

    private int getDefaultLimitByProvider(String provider) {
        if (provider == null) return 2;
        String p = provider.toLowerCase().trim();
        if ("dashscope".equals(p)) {
            return 2; // 阿里云 DashScope 在途并发上限默认为 2
        }
        if ("doubao".equals(p)) {
            return 5; // 火山引擎/豆包并发上限为 5
        }
        if ("openai".equals(p)) {
            return 4; // OpenAI / 第三方中转默认 4
        }
        return 2; // 默认 2
    }
}
