package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.modelcenter.config.ModelCenterRuntimeProperties;
import com.polaris.ai.runtime.RuntimePolicySpec;
import com.polaris.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Redis 集群级限流实现；仅显式 LOCAL 模式使用 JVM fallback。 */
@Component
public class DefaultModelExecutionLimiter implements ModelExecutionLimiter {

    static final String QPS_LUA = """
            local scale = 10000
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            local rate = tonumber(ARGV[1])
            local capacity = math.max(rate, scale)
            local stored = redis.call('HMGET', KEYS[1], 'tokens', 'timestamp', 'rate')
            local tokens = tonumber(stored[1]) or capacity
            local timestamp = tonumber(stored[2]) or now
            local oldRate = tonumber(stored[3])
            if oldRate and oldRate ~= rate then tokens = math.min(tokens, capacity) end
            tokens = math.min(capacity, tokens + math.max(0, now - timestamp) * rate / 1000)
            local allowed = 0
            if tokens >= scale then
              tokens = tokens - scale
              allowed = 1
            end
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'timestamp', now, 'rate', rate)
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return allowed
            """;

    static final String CONCURRENCY_ACQUIRE_LUA = """
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[1]) then return 0 end
            redis.call('ZADD', KEYS[1], now + tonumber(ARGV[3]), ARGV[2])
            redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[3]) + 10000)
            return 1
            """;

    private static final Logger log = LoggerFactory.getLogger(
            DefaultModelExecutionLimiter.class);
    private static final Pattern CAPABILITY_CODE =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");
    private static final ModelExecutionPermit NOOP_PERMIT = () -> { };

    private final ModelCenterRuntimeProperties properties;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> qpsScript =
            new DefaultRedisScript<>(QPS_LUA, Long.class);
    private final DefaultRedisScript<Long> concurrencyScript =
            new DefaultRedisScript<>(CONCURRENCY_ACQUIRE_LUA, Long.class);
    private final ConcurrentMap<String, LocalTokenBucket> localBuckets =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LocalSemaphore> localSemaphores =
            new ConcurrentHashMap<>();

    public DefaultModelExecutionLimiter(
            ModelCenterRuntimeProperties properties,
            RedisConnectionFactory connectionFactory) {
        this.properties = properties;
        this.redis = new StringRedisTemplate(connectionFactory);
    }

    @Override
    public ModelExecutionPermit acquire(
            Long modelId,
            String capabilityCode,
            RuntimePolicySpec policy) {
        if (modelId == null || modelId <= 0 || policy == null) {
            throw new ServiceException("MODEL_RUNTIME_POLICY_INVALID");
        }
        String capability = normalizeCapability(capabilityCode);
        String key = "model:" + modelId + ":cap:" + capability;
        if (policy.qpsLimit() != null && !tryAcquireQps(key, policy)) {
            throw new ServiceException("MODEL_RATE_LIMITED");
        }
        if (policy.maxConcurrency() == null) {
            return NOOP_PERMIT;
        }
        return acquireConcurrency(key, policy);
    }

    private boolean tryAcquireQps(String key, RuntimePolicySpec policy) {
        if (properties.getLimiterMode()
                == ModelCenterRuntimeProperties.LimiterMode.LOCAL) {
            return localBuckets.computeIfAbsent(key,
                    ignored -> new LocalTokenBucket())
                    .tryAcquire(policy.qpsLimit().doubleValue());
        }
        try {
            long rateUnits = policy.qpsLimit().movePointRight(4)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
            long ttlMillis = qpsTtlMillis(policy.qpsLimit().doubleValue());
            Long result = redis.execute(qpsScript,
                    List.of(redisKey(key, "qps")),
                    Long.toString(rateUnits), Long.toString(ttlMillis));
            return Long.valueOf(1).equals(result);
        } catch (RuntimeException e) {
            throw new ServiceException("MODEL_RUNTIME_LIMITER_UNAVAILABLE");
        }
    }

    private ModelExecutionPermit acquireConcurrency(
            String key,
            RuntimePolicySpec policy) {
        if (properties.getLimiterMode()
                == ModelCenterRuntimeProperties.LimiterMode.LOCAL) {
            LocalSemaphore holder = localSemaphores.compute(key,
                    (ignored, current) -> current == null
                            || current.limit() != policy.maxConcurrency()
                            ? new LocalSemaphore(policy.maxConcurrency())
                            : current);
            if (!holder.semaphore().tryAcquire()) {
                throw new ServiceException("MODEL_RATE_LIMITED");
            }
            AtomicBoolean released = new AtomicBoolean();
            return () -> {
                if (released.compareAndSet(false, true)) {
                    holder.semaphore().release();
                }
            };
        }
        String redisKey = redisKey(key, "concurrency");
        String token = UUID.randomUUID().toString();
        long leaseMillis = concurrencyLeaseMillis(policy);
        try {
            Long result = redis.execute(concurrencyScript, List.of(redisKey),
                    Integer.toString(policy.maxConcurrency()), token,
                    Long.toString(leaseMillis));
            if (!Long.valueOf(1).equals(result)) {
                throw new ServiceException("MODEL_RATE_LIMITED");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceException("MODEL_RUNTIME_LIMITER_UNAVAILABLE");
        }
        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (!released.compareAndSet(false, true)) {
                return;
            }
            try {
                redis.opsForZSet().remove(redisKey, token);
            } catch (RuntimeException e) {
                log.warn("Model concurrency permit release failed modelKey={}",
                        key);
            }
        };
    }

    private long qpsTtlMillis(double qps) {
        return Math.min(Duration.ofDays(1).toMillis(),
                Math.max(2_000L, (long) Math.ceil(2_000D / qps)));
    }

    private long concurrencyLeaseMillis(RuntimePolicySpec policy) {
        long requested = (long) policy.connectTimeoutMs()
                + policy.readTimeoutMs() + 30_000L;
        return Math.min(Duration.ofMinutes(10).toMillis(),
                Math.max(Duration.ofMinutes(1).toMillis(), requested));
    }

    private String redisKey(String key, String dimension) {
        String prefix = properties.getLimiterKeyPrefix();
        if (prefix == null || prefix.isBlank() || prefix.length() > 100) {
            throw new ServiceException("MODEL_RUNTIME_LIMITER_INVALID");
        }
        return prefix + key + ":" + dimension;
    }

    private String normalizeCapability(String value) {
        String code = value == null
                ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!CAPABILITY_CODE.matcher(code).matches()) {
            throw new ServiceException("CAPABILITY_NOT_ENABLED");
        }
        return code;
    }

    private static final class LocalTokenBucket {
        private double tokens;
        private double rate;
        private boolean initialized;
        private long lastRefillNanos = System.nanoTime();

        private synchronized boolean tryAcquire(double requestedRate) {
            long now = System.nanoTime();
            if (!initialized) {
                rate = requestedRate;
                tokens = capacity();
                initialized = true;
            } else if (rate != requestedRate) {
                rate = requestedRate;
                tokens = Math.min(tokens, capacity());
            }
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000D;
            tokens = Math.min(capacity(), tokens + elapsedSeconds * rate);
            lastRefillNanos = now;
            if (tokens < 1D) {
                return false;
            }
            tokens -= 1D;
            return true;
        }

        private double capacity() {
            return Math.max(1D, rate);
        }
    }

    private record LocalSemaphore(int limit, Semaphore semaphore) {

        private LocalSemaphore(int limit) {
            this(limit, new Semaphore(limit, true));
        }
    }
}
