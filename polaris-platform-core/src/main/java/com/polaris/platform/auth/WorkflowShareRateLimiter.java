package com.polaris.platform.auth;

import com.polaris.common.constant.CacheConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.util.Collections;

/** 工作流分享的固定窗口限流器。 */
@Component
public class WorkflowShareRateLimiter {

    private static final int WINDOW_SECONDS = 60;
    private static final int DEFAULT_RATE_LIMIT = 60;
    private static final int MAX_RATE_LIMIT = 10000;
    private static final long DEFAULT_DAILY_UPLOAD_BYTES = 100L * 1024 * 1024;
    private static final String KEY_PREFIX = CacheConstants.RATE_LIMIT_KEY + "workflow_share:";
    private static final String UPLOAD_KEY_PREFIX = KEY_PREFIX + "upload:";
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = buildScript();
    private static final DefaultRedisScript<Long> UPLOAD_QUOTA_SCRIPT = buildUploadQuotaScript();

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Value("${platform.workflow-share.read-rate-limit:120}")
    private int readRateLimit = 120;

    @Value("${platform.workflow-share.upload-rate-limit:20}")
    private int uploadRateLimit = 20;

    public enum RequestType {
        EXECUTION, READ, UPLOAD
    }

    public boolean tryAcquire(Long shareId, Integer configuredLimit) {
        return tryAcquire(shareId, configuredLimit, RequestType.EXECUTION);
    }

    public boolean tryAcquire(Long shareId, Integer configuredLimit, RequestType type) {
        if (shareId == null) {
            throw new IllegalArgumentException("分享ID不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("分享请求类型不能为空");
        }
        int executionLimit = configuredLimit == null || configuredLimit <= 0
                ? DEFAULT_RATE_LIMIT : Math.min(configuredLimit, MAX_RATE_LIMIT);
        int limit = switch (type) {
            case EXECUTION -> executionLimit;
            case READ -> Math.max(1, Math.min(readRateLimit, MAX_RATE_LIMIT));
            case UPLOAD -> Math.max(1, Math.min(uploadRateLimit, MAX_RATE_LIMIT));
        };
        // Keep the existing daily-byte quota namespace; upload request counts must
        // never use "upload:", otherwise INCR and INCRBY corrupt each other's totals/TTL.
        String bucket = type == RequestType.UPLOAD ? "upload_requests"
                : type.name().toLowerCase(java.util.Locale.ROOT);
        Long current = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(KEY_PREFIX + bucket + ":" + shareId),
                WINDOW_SECONDS);
        if (current == null) {
            throw new IllegalStateException("分享限流服务未返回结果");
        }
        return current <= limit;
    }

    public boolean tryConsumeUploadBytes(Long shareId, long bytes, Long configuredLimit) {
        if (shareId == null || bytes <= 0) {
            throw new IllegalArgumentException("分享ID和上传大小不能为空");
        }
        long limit = configuredLimit == null || configuredLimit <= 0
                ? DEFAULT_DAILY_UPLOAD_BYTES : configuredLimit;
        Long current = redisTemplate.execute(
                UPLOAD_QUOTA_SCRIPT,
                StringRedisSerializer.UTF_8,
                new GenericToStringSerializer<>(Long.class),
                Collections.singletonList(UPLOAD_KEY_PREFIX + shareId),
                Long.toString(bytes),
                Integer.toString(24 * 60 * 60));
        if (current == null) {
            throw new IllegalStateException("上传配额服务未返回结果");
        }
        return current <= limit;
    }

    private static DefaultRedisScript<Long> buildScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "local current = redis.call('incr', KEYS[1])\n" +
                "if tonumber(current) == 1 then\n" +
                "    redis.call('expire', KEYS[1], ARGV[1])\n" +
                "end\n" +
                "return current");
        return script;
    }

    private static DefaultRedisScript<Long> buildUploadQuotaScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "local current = redis.call('incrby', KEYS[1], ARGV[1])\n" +
                "if tonumber(current) == tonumber(ARGV[1]) then\n" +
                "    redis.call('expire', KEYS[1], ARGV[2])\n" +
                "end\n" +
                "return current");
        return script;
    }
}
