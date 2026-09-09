package com.polaris.platform.auth;

import com.polaris.common.constant.CacheConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * API Key 动态限流器
 */
@Component
public class ApiKeyRateLimiter {

    private static final int WINDOW_SECONDS = 60;
    private static final int DEFAULT_RATE_LIMIT = 60;
    private static final int MAX_RATE_LIMIT = 10000;
    /** Redis 脚本结果编码为 current * 1000 + ttl，限流窗口需小于 1000 秒 */
    private static final long RESULT_FACTOR = 1000L;
    private static final String RATE_LIMIT_KEY_PREFIX = CacheConstants.RATE_LIMIT_KEY + "platform_api_key:";
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = buildRateLimitScript();

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    /**
     * 获取一次调用额度
     */
    public RateLimitResult tryAcquire(Long apiKeyId, Integer configuredLimit) {
        if (apiKeyId == null) {
            throw new IllegalArgumentException("API Key ID 不能为空");
        }

        int limit = normalizeLimit(configuredLimit);
        String redisKey = RATE_LIMIT_KEY_PREFIX + apiKeyId;
        Long encodedResult = redisTemplate.execute(
                RATE_LIMIT_SCRIPT, Collections.singletonList(redisKey), limit, WINDOW_SECONDS);
        if (encodedResult == null) {
            throw new IllegalStateException("API Key 限流服务未返回结果");
        }

        long current = encodedResult / RESULT_FACTOR;
        long retryAfterSeconds = Math.max(1L, encodedResult % RESULT_FACTOR);
        long remaining = Math.max(0L, limit - current);
        return new RateLimitResult(current <= limit, limit, remaining, retryAfterSeconds);
    }

    private int normalizeLimit(Integer configuredLimit) {
        if (configuredLimit == null || configuredLimit <= 0) {
            return DEFAULT_RATE_LIMIT;
        }
        return Math.min(configuredLimit, MAX_RATE_LIMIT);
    }

    private static DefaultRedisScript<Long> buildRateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "local key = KEYS[1]\n" +
                "local count = tonumber(ARGV[1])\n" +
                "local time = tonumber(ARGV[2])\n" +
                "local current = redis.call('get', key)\n" +
                "if current and tonumber(current) > count then\n" +
                "    local ttl = redis.call('ttl', key)\n" +
                "    if tonumber(ttl) < 0 then\n" +
                "        redis.call('expire', key, time)\n" +
                "        ttl = time\n" +
                "    end\n" +
                "    return tonumber(current) * 1000 + tonumber(ttl)\n" +
                "end\n" +
                "current = redis.call('incr', key)\n" +
                "if tonumber(current) == 1 then\n" +
                "    redis.call('expire', key, time)\n" +
                "end\n" +
                "local ttl = redis.call('ttl', key)\n" +
                "if tonumber(ttl) < 0 then\n" +
                "    redis.call('expire', key, time)\n" +
                "    ttl = time\n" +
                "end\n" +
                "return tonumber(current) * 1000 + tonumber(ttl)");
        return script;
    }

    /**
     * API Key 限流结果
     */
    public record RateLimitResult(boolean allowed, int limit, long remaining, long retryAfterSeconds) {
    }
}
