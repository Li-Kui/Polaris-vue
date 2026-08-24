package com.polaris.platform.auth;

import com.polaris.common.constant.CacheConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 中台登录限流器
 */
@Slf4j
@Component
public class PlatformLoginRateLimiter {

    private static final int IP_LIMIT = 30;
    private static final int IP_WINDOW_SECONDS = 600;
    private static final int ACCOUNT_LIMIT = 5;
    private static final int ACCOUNT_WINDOW_SECONDS = 900;
    private static final long RESULT_FACTOR = 1000000L;
    private static final String IP_KEY_PREFIX = CacheConstants.RATE_LIMIT_KEY + "platform_login:ip:";
    private static final String ACCOUNT_KEY_PREFIX = CacheConstants.RATE_LIMIT_KEY + "platform_login:account:";
    private static final DefaultRedisScript<Long> LOGIN_LIMIT_SCRIPT = buildLoginLimitScript();

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 获取一次登录尝试额度
     */
    public LoginAttemptResult tryAcquire(String sourceIp, String tenantCode, String username) {
        String ipKey = IP_KEY_PREFIX + digest(normalize(sourceIp));
        String accountKey = ACCOUNT_KEY_PREFIX + accountFingerprint(tenantCode, username);
        Long encodedResult = redisTemplate.execute(
                LOGIN_LIMIT_SCRIPT,
                List.of(ipKey, accountKey),
                String.valueOf(IP_LIMIT),
                String.valueOf(IP_WINDOW_SECONDS),
                String.valueOf(ACCOUNT_LIMIT),
                String.valueOf(ACCOUNT_WINDOW_SECONDS),
                String.valueOf(RESULT_FACTOR));
        if (encodedResult == null) {
            throw new IllegalStateException("中台登录限流服务未返回结果");
        }

        long resultCode = encodedResult / RESULT_FACTOR;
        long retryAfterSeconds = Math.max(1L, encodedResult % RESULT_FACTOR);
        if (resultCode == 1L) {
            return new LoginAttemptResult(true, LimitDimension.NONE, 0L);
        }
        if (resultCode == 2L) {
            return new LoginAttemptResult(false, LimitDimension.IP, retryAfterSeconds);
        }
        if (resultCode == 3L) {
            return new LoginAttemptResult(false, LimitDimension.ACCOUNT, retryAfterSeconds);
        }
        throw new IllegalStateException("中台登录限流服务返回未知状态");
    }

    /**
     * 登录成功后清理账号维度失败计数
     */
    public void clearAccountFailures(String tenantCode, String username) {
        try {
            redisTemplate.delete(ACCOUNT_KEY_PREFIX + accountFingerprint(tenantCode, username));
        } catch (Exception e) {
            log.warn("清理中台登录失败计数异常, account={}", accountFingerprint(tenantCode, username), e);
        }
    }

    public String accountFingerprint(String tenantCode, String username) {
        return digest(normalize(tenantCode) + "\u0000" + normalize(username));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前环境不支持 SHA-256", e);
        }
    }

    private static DefaultRedisScript<Long> buildLoginLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "local ipLimit = tonumber(ARGV[1])\n" +
                "local ipWindow = tonumber(ARGV[2])\n" +
                "local accountLimit = tonumber(ARGV[3])\n" +
                "local accountWindow = tonumber(ARGV[4])\n" +
                "local factor = tonumber(ARGV[5])\n" +
                "local ipCurrent = tonumber(redis.call('get', KEYS[1])) or 0\n" +
                "if ipCurrent >= ipLimit then\n" +
                "    local ttl = redis.call('ttl', KEYS[1])\n" +
                "    if ttl < 0 then redis.call('expire', KEYS[1], ipWindow); ttl = ipWindow end\n" +
                "    return 2 * factor + ttl\n" +
                "end\n" +
                "ipCurrent = redis.call('incr', KEYS[1])\n" +
                "if ipCurrent == 1 then redis.call('expire', KEYS[1], ipWindow) end\n" +
                "local accountCurrent = tonumber(redis.call('get', KEYS[2])) or 0\n" +
                "if accountCurrent >= accountLimit then\n" +
                "    local ttl = redis.call('ttl', KEYS[2])\n" +
                "    if ttl < 0 then redis.call('expire', KEYS[2], accountWindow); ttl = accountWindow end\n" +
                "    return 3 * factor + ttl\n" +
                "end\n" +
                "accountCurrent = redis.call('incr', KEYS[2])\n" +
                "if accountCurrent == 1 then redis.call('expire', KEYS[2], accountWindow) end\n" +
                "return factor");
        return script;
    }

    public record LoginAttemptResult(boolean allowed, LimitDimension dimension, long retryAfterSeconds) {
    }

    public enum LimitDimension {
        NONE,
        IP,
        ACCOUNT
    }
}
