package com.polaris.framework.config;

import com.anji.captcha.service.CaptchaCacheService;
import com.anji.captcha.service.impl.CaptchaServiceFactory;
import com.polaris.common.core.redis.RedisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.TimeUnit;

/**
 * AJ-Captcha 配置类
 * 
 * @author polaris
 */
@Configuration
@Import(com.anji.captcha.config.AjCaptchaAutoConfiguration.class)
public class AjCaptchaConfig {

    @Autowired
    private RedisCache redisCache;

    @Bean
    public CaptchaCacheService captchaCacheService() {
        CaptchaCacheService myCache = new CaptchaCacheService() {
            @Override
            public void set(String key, String value, long expiresInSeconds) {
                redisCache.setCacheObject(key, value, (int) expiresInSeconds, TimeUnit.SECONDS);
            }

            @Override
            public boolean exists(String key) {
                return redisCache.hasKey(key);
            }

            @Override
            public String get(String key) {
                return redisCache.getCacheObject(key);
            }

            @Override
            public void delete(String key) {
                redisCache.deleteObject(key);
            }

            @Override
            public String type() {
                return "redis";
            }
        };

        // 手动注册到 AJ-Captcha 的静态缓存 Map 中，无论配置文件设为 local 还是 redis，均强制使用 Spring 管理的 RedisCache，极大提升集群化高可用防错能力
        CaptchaServiceFactory.cacheService.put("local", myCache);
        CaptchaServiceFactory.cacheService.put("redis", myCache);

        return myCache;
    }
}
