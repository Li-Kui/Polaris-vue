package com.polaris.platform.service;

import com.polaris.platform.domain.Tenant;
import com.polaris.platform.mapper.TenantMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

/**
 * 租户 Token 配额服务
 */
@Slf4j
@Service
public class TokenQuotaService {

    private static final String QUOTA_KEY_PREFIX = "platform:tenant:token_quota:";
    private static final String RESERVATION_FIELD_PREFIX = "reservation:";
    private static final long RESERVATION_TTL_SECONDS = 600L;
    private static final long STATE_TTL_SECONDS = 1800L;
    private static final long CACHE_MISSING = -2L;
    private static final long QUOTA_EXCEEDED = -1L;
    private static final long LIMITED_RESERVED = 1L;
    private static final long UNLIMITED = 2L;

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = buildReserveScript(false);
    private static final DefaultRedisScript<Long> INIT_AND_RESERVE_SCRIPT = buildReserveScript(true);
    private static final DefaultRedisScript<Long> SETTLE_SCRIPT = buildSettleScript();
    private static final DefaultRedisScript<Long> REFRESH_SCRIPT = buildRefreshScript();

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 为一次模型调用预占 Token 配额
     */
    public TokenReservation reserve(String tenantId, int estimatedTokens) {
        Long parsedTenantId = parseTenantId(tenantId);
        int tokens = Math.max(1, estimatedTokens);
        String reservationId = UUID.randomUUID().toString();

        try {
            Long result = executeReserve(RESERVE_SCRIPT, parsedTenantId, reservationId, tokens, null);
            if (Long.valueOf(CACHE_MISSING).equals(result)) {
                Tenant tenant = loadTenant(parsedTenantId);
                result = executeReserve(INIT_AND_RESERVE_SCRIPT, parsedTenantId, reservationId, tokens, tenant);
            }
            return buildReservation(parsedTenantId, reservationId, tokens, result);
        } catch (TokenQuotaExceededException | TokenQuotaUnavailableException e) {
            throw e;
        } catch (Exception e) {
            Tenant tenant = loadTenantSafely(parsedTenantId);
            if (tenant != null && Long.valueOf(-1L).equals(tenant.getQuotaTokens())) {
                log.warn("租户[{}]配额缓存不可用，已确认该租户为无限配额", parsedTenantId);
                return TokenReservation.unlimited(parsedTenantId);
            }
            log.error("租户[{}] Token 配额服务不可用", parsedTenantId, e);
            throw new TokenQuotaUnavailableException("Token 配额服务暂不可用，请稍后重试", e);
        }
    }

    /**
     * 按实际 Token 用量结算预占额度
     */
    public void complete(TokenReservation reservation, int actualTokens) {
        if (reservation == null) {
            return;
        }
        if (actualTokens <= 0) {
            release(reservation);
            return;
        }

        int updated = tenantMapper.updateUsedTokens(reservation.tenantId(), actualTokens);
        if (updated <= 0) {
            throw new TokenQuotaUnavailableException("租户 Token 用量更新失败");
        }

        Tenant tenant = tenantMapper.selectById(reservation.tenantId());
        if (tenant == null || tenant.getUsedTokens() == null) {
            throw new TokenQuotaUnavailableException("租户 Token 用量读取失败");
        }

        try {
            redisTemplate.execute(
                    SETTLE_SCRIPT,
                    Collections.singletonList(buildQuotaKey(reservation.tenantId())),
                    reservation.fieldName(),
                    String.valueOf(tenant.getUsedTokens()),
                    String.valueOf(STATE_TTL_SECONDS));
        } catch (Exception e) {
            log.error("租户[{}] Token 配额缓存结算失败，数据库用量已更新", reservation.tenantId(), e);
        }
    }

    /**
     * 模型调用失败时释放预占额度
     */
    public void release(TokenReservation reservation) {
        if (reservation == null || !reservation.limited()) {
            return;
        }
        try {
            redisTemplate.opsForHash().delete(buildQuotaKey(reservation.tenantId()), reservation.fieldName());
        } catch (Exception e) {
            log.warn("释放租户[{}] Token 预占额度失败，将由过期机制自动清理", reservation.tenantId(), e);
        }
    }

    /**
     * 租户配额配置变化后刷新缓存
     */
    public void refresh(Tenant tenant) {
        if (tenant == null || tenant.getTenantId() == null || tenant.getQuotaTokens() == null) {
            return;
        }
        long usedTokens = tenant.getUsedTokens() == null ? 0L : tenant.getUsedTokens();
        try {
            redisTemplate.execute(
                    REFRESH_SCRIPT,
                    Collections.singletonList(buildQuotaKey(tenant.getTenantId())),
                    String.valueOf(tenant.getQuotaTokens()),
                    String.valueOf(usedTokens),
                    String.valueOf(STATE_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("刷新租户[{}] Token 配额缓存失败", tenant.getTenantId(), e);
        }
    }

    public void evict(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        try {
            redisTemplate.delete(buildQuotaKey(tenantId));
        } catch (Exception e) {
            log.warn("删除租户[{}] Token 配额缓存失败", tenantId, e);
        }
    }

    private Long executeReserve(DefaultRedisScript<Long> script, Long tenantId, String reservationId,
                                int tokens, Tenant tenant) {
        long now = System.currentTimeMillis() / 1000;
        String key = buildQuotaKey(tenantId);
        String field = RESERVATION_FIELD_PREFIX + reservationId;
        Long result;
        if (tenant == null) {
            result = redisTemplate.execute(
                    script, Collections.singletonList(key), field, String.valueOf(tokens),
                    String.valueOf(now), String.valueOf(now + RESERVATION_TTL_SECONDS),
                    String.valueOf(STATE_TTL_SECONDS));
        } else {
            if (tenant.getQuotaTokens() == null) {
                throw new TokenQuotaUnavailableException("租户 Token 配额配置无效");
            }
            long usedTokens = tenant.getUsedTokens() == null ? 0L : tenant.getUsedTokens();
            result = redisTemplate.execute(
                    script, Collections.singletonList(key), field, String.valueOf(tokens),
                    String.valueOf(now), String.valueOf(now + RESERVATION_TTL_SECONDS),
                    String.valueOf(STATE_TTL_SECONDS), String.valueOf(tenant.getQuotaTokens()),
                    String.valueOf(usedTokens));
        }
        if (result == null) {
            throw new TokenQuotaUnavailableException("Token 配额服务未返回结果");
        }
        return result;
    }

    private TokenReservation buildReservation(Long tenantId, String reservationId, int tokens, Long result) {
        if (Long.valueOf(QUOTA_EXCEEDED).equals(result)) {
            throw new TokenQuotaExceededException("租户 Token 配额已耗尽，请联系管理员充值");
        }
        if (Long.valueOf(LIMITED_RESERVED).equals(result)) {
            return TokenReservation.limited(tenantId, reservationId, tokens);
        }
        if (Long.valueOf(UNLIMITED).equals(result)) {
            return TokenReservation.unlimited(tenantId);
        }
        throw new TokenQuotaUnavailableException("Token 配额服务返回未知状态");
    }

    private Tenant loadTenant(Long tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new TokenQuotaUnavailableException("租户不存在或已被删除");
        }
        return tenant;
    }

    private Tenant loadTenantSafely(Long tenantId) {
        try {
            return tenantMapper.selectById(tenantId);
        } catch (Exception e) {
            log.error("读取租户[{}] Token 配额失败", tenantId, e);
            return null;
        }
    }

    private Long parseTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new TokenQuotaUnavailableException("无法识别当前租户");
        }
        try {
            return Long.parseLong(tenantId);
        } catch (NumberFormatException e) {
            throw new TokenQuotaUnavailableException("当前租户标识无效", e);
        }
    }

    private String buildQuotaKey(Long tenantId) {
        return QUOTA_KEY_PREFIX + tenantId;
    }

    private static DefaultRedisScript<Long> buildReserveScript(boolean initialize) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        String initializeScript = initialize
                ? "if redis.call('exists', KEYS[1]) == 0 then\n" +
                  "    redis.call('hset', KEYS[1], 'quota', ARGV[6], 'used', ARGV[7])\n" +
                  "end\n"
                : "if redis.call('exists', KEYS[1]) == 0 then return -2 end\n";
        script.setScriptText(
                initializeScript +
                "local now = tonumber(ARGV[3])\n" +
                "local entries = redis.call('hgetall', KEYS[1])\n" +
                "local reserved = 0\n" +
                "for i = 1, #entries, 2 do\n" +
                "    local field = entries[i]\n" +
                "    if string.sub(field, 1, 12) == 'reservation:' then\n" +
                "        local value = entries[i + 1]\n" +
                "        local separator = string.find(value, ':')\n" +
                "        local amount = separator and tonumber(string.sub(value, 1, separator - 1)) or nil\n" +
                "        local expires = separator and tonumber(string.sub(value, separator + 1)) or nil\n" +
                "        if not amount or not expires or expires <= now then\n" +
                "            redis.call('hdel', KEYS[1], field)\n" +
                "        else\n" +
                "            reserved = reserved + amount\n" +
                "        end\n" +
                "    end\n" +
                "end\n" +
                "local quota = tonumber(redis.call('hget', KEYS[1], 'quota'))\n" +
                "local used = tonumber(redis.call('hget', KEYS[1], 'used')) or 0\n" +
                "local tokens = tonumber(ARGV[2])\n" +
                "if not quota or not tokens then return -3 end\n" +
                "redis.call('expire', KEYS[1], ARGV[5])\n" +
                "if quota < 0 then return 2 end\n" +
                "if used + reserved + tokens > quota then return -1 end\n" +
                "redis.call('hset', KEYS[1], ARGV[1], ARGV[2] .. ':' .. ARGV[4])\n" +
                "return 1");
        return script;
    }

    private static DefaultRedisScript<Long> buildSettleScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "if redis.call('exists', KEYS[1]) == 0 then return 0 end\n" +
                "redis.call('hdel', KEYS[1], ARGV[1])\n" +
                "local current = tonumber(redis.call('hget', KEYS[1], 'used')) or 0\n" +
                "local durable = tonumber(ARGV[2])\n" +
                "if durable and durable > current then redis.call('hset', KEYS[1], 'used', durable) end\n" +
                "redis.call('expire', KEYS[1], ARGV[3])\n" +
                "return 1");
        return script;
    }

    private static DefaultRedisScript<Long> buildRefreshScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "redis.call('hset', KEYS[1], 'quota', ARGV[1], 'used', ARGV[2])\n" +
                "redis.call('expire', KEYS[1], ARGV[3])\n" +
                "return 1");
        return script;
    }

    /**
     * 单次模型调用的 Token 预占凭证
     */
    public record TokenReservation(Long tenantId, String reservationId, int reservedTokens, boolean limited) {

        public static TokenReservation limited(Long tenantId, String reservationId, int reservedTokens) {
            return new TokenReservation(tenantId, reservationId, reservedTokens, true);
        }

        public static TokenReservation unlimited(Long tenantId) {
            return new TokenReservation(tenantId, null, 0, false);
        }

        public String fieldName() {
            return limited ? RESERVATION_FIELD_PREFIX + reservationId : "";
        }
    }

    public static class TokenQuotaExceededException extends RuntimeException {
        public TokenQuotaExceededException(String message) {
            super(message);
        }
    }

    public static class TokenQuotaUnavailableException extends RuntimeException {
        public TokenQuotaUnavailableException(String message) {
            super(message);
        }

        public TokenQuotaUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
