package com.polaris.platform.service;

import com.polaris.common.exception.ServiceException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.List;

/** 渠道共享限额，Redis 原子计数；缓存不可用时不放行收费调用。 */
@Component
public class CustomerVisitorRateLimiter {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> COUNT = new DefaultRedisScript<>(
            "local n=redis.call('incr',KEYS[1]); if n==1 then redis.call('expire',KEYS[1],ARGV[1]) end; return n", Long.class);
    public CustomerVisitorRateLimiter(StringRedisTemplate redis) { this.redis = redis; }
    public void acquire(String channel, String bucket, String subject, int limit, int seconds) {
        Long count;
        try { count = redis.execute(COUNT, List.of("platform:customer-visitor:" + channel + ":" + bucket + ":" + subject), Integer.toString(seconds)); }
        catch (RuntimeException failure) { throw new ServiceException("RATE_LIMIT_UNAVAILABLE: 接待限额服务暂不可用", 503); }
        if (count == null) throw new ServiceException("RATE_LIMIT_UNAVAILABLE: 接待限额服务暂不可用", 503);
        if (count > limit) throw new ServiceException("RATE_LIMITED: 接待请求较多，请稍后再试", 429);
    }
}
