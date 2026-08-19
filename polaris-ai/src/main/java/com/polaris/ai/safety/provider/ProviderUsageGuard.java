package com.polaris.ai.safety.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.safety.config.ModerationProviderProperties;
import com.polaris.ai.safety.dto.ProviderResult;
import com.polaris.ai.safety.model.ModerationPolicy;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.model.ProviderDecision;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Redis-backed provider result cache and fail-closed atomic usage budget. */
public final class ProviderUsageGuard {
    static final Duration CACHE_TTL = Duration.ofHours(24);
    static final Duration DAILY_TTL = Duration.ofHours(48);
    static final Duration MONTHLY_TTL = Duration.ofDays(40);
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String HASH_PATTERN = "[0-9a-f]{64}";
    private static final String SAFE_CODE_PATTERN = "[a-z0-9_-]{1,64}";
    private static final int MONEY_SCALE = 8;
    private static final int MAX_COUNTER_DIGITS = 30;
    private static final Set<String> CACHE_FIELDS = Set.of(
            "provider", "decision", "riskScore", "categories",
            "providerRequestId", "latencyMs");

    private final ProviderStateStore store;
    private final ObjectMapper objectMapper;
    private final BigDecimal estimatedCost;
    private final Clock clock;

    public ProviderUsageGuard(ProviderStateStore store, ObjectMapper objectMapper,
                              ModerationProviderProperties properties, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.estimatedCost = properties.getEstimatedCostPerCall();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean tryAcquire(ModerationPolicy policy, String normalizedHash) {
        return acquire(policy, normalizedHash).outcome() == AcquireOutcome.ACQUIRED;
    }

    public Acquisition acquire(ModerationPolicy policy, String normalizedHash) {
        validateTuple(policy, normalizedHash);
        if (!positiveAndBoundedCost(estimatedCost)
                || policy.getProviderDailyLimit() == null || policy.getProviderDailyLimit() <= 0
                || policy.getProviderMonthlyBudget() == null
                || policy.getProviderMonthlyBudget().compareTo(BigDecimal.ZERO) <= 0
                || !boundedBudget(policy.getProviderMonthlyBudget())) {
            return new Acquisition(AcquireOutcome.BUDGET_DISABLED);
        }
        String costUnits = costUnitsCeiling(estimatedCost);
        String budgetUnits = budgetUnitsFloor(policy.getProviderMonthlyBudget());
        if ("0".equals(budgetUnits)) {
            return new Acquisition(AcquireOutcome.MONTHLY_BUDGET);
        }
        LocalDate date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        try {
            return new Acquisition(store.acquireBudget(
                    "ai:moderation:provider:daily:" + DAY.format(date),
                    policy.getProviderDailyLimit(), DAILY_TTL,
                    "ai:moderation:provider:monthly:" + MONTH.format(date),
                    budgetUnits, costUnits, MONTHLY_TTL));
        } catch (RuntimeException unavailable) {
            return new Acquisition(AcquireOutcome.REDIS_UNAVAILABLE);
        }
    }

    public CacheLookup lookup(
            ModerationPolicy policy, ModerationScene scene, String normalizedHash) {
        return lookup(policy, scene, normalizedHash, "aliyun", 60_000);
    }

    public CacheLookup lookup(
            ModerationPolicy policy, ModerationScene scene, String normalizedHash,
            String expectedProvider, int timeoutMs) {
        String key = cacheKey(policy, scene, normalizedHash);
        String json;
        try {
            json = store.get(key);
            if (json == null) {
                return new CacheLookup(CacheOutcome.MISS, Optional.empty());
            }
        } catch (RuntimeException redisUnavailable) {
            return new CacheLookup(CacheOutcome.REDIS_UNAVAILABLE, Optional.empty());
        }
        JsonNode tree;
        try {
            tree = objectMapper.readTree(json);
        } catch (JsonProcessingException malformedJson) {
            return new CacheLookup(CacheOutcome.REDIS_UNAVAILABLE, Optional.empty());
        }
        try {
            if (!exactSchema(tree)) {
                return new CacheLookup(CacheOutcome.MISS, Optional.empty());
            }
            CachedProviderResult cached = objectMapper.treeToValue(
                    tree, CachedProviderResult.class);
            if (!validCached(cached, expectedProvider, timeoutMs)) {
                return new CacheLookup(CacheOutcome.MISS, Optional.empty());
            }
            ProviderResult result = new ProviderResult(cached.provider(), cached.decision(),
                    cached.riskScore(), cached.categories(), cached.providerRequestId(),
                    cached.latencyMs());
            return new CacheLookup(CacheOutcome.HIT, Optional.of(result));
        } catch (RuntimeException | JsonProcessingException invalidValue) {
            return new CacheLookup(CacheOutcome.MISS, Optional.empty());
        }
    }

    public void cache(ModerationPolicy policy, ModerationScene scene,
                      String normalizedHash, ProviderResult result) {
        Objects.requireNonNull(result, "result");
        if (result.decision() != ProviderDecision.PASS
                && result.decision() != ProviderDecision.HIGH_RISK) {
            return;
        }
        String key = cacheKey(policy, scene, normalizedHash);
        try {
            CachedProviderResult cached = new CachedProviderResult(
                    result.provider(), result.decision(), result.riskScore(),
                    result.categories(), result.providerRequestId(), result.latencyMs());
            store.put(key, objectMapper.writeValueAsString(cached), CACHE_TTL);
        } catch (RuntimeException | JsonProcessingException ignored) {
            // The completed provider result remains usable; a cache write cannot undo it.
        }
    }

    private static String cacheKey(
            ModerationPolicy policy, ModerationScene scene, String normalizedHash) {
        validateTuple(policy, normalizedHash);
        Objects.requireNonNull(scene, "scene");
        return "ai:moderation:provider:cache:" + policy.getPolicyVersion()
                + ":" + scene.name() + ":" + normalizedHash;
    }

    private static void validateTuple(ModerationPolicy policy, String normalizedHash) {
        Objects.requireNonNull(policy, "policy");
        if (policy.getPolicyVersion() == null || policy.getPolicyVersion() <= 0) {
            throw new IllegalArgumentException("policyVersion must be positive");
        }
        if (normalizedHash == null || !normalizedHash.matches(HASH_PATTERN)) {
            throw new IllegalArgumentException("normalizedHash must be 64 lowercase hex characters");
        }
    }

    private static boolean exactSchema(JsonNode tree) {
        if (tree == null || !tree.isObject() || tree.size() != CACHE_FIELDS.size()) {
            return false;
        }
        Set<String> fields = new HashSet<>();
        tree.fieldNames().forEachRemaining(fields::add);
        return fields.equals(CACHE_FIELDS)
                && tree.get("categories") != null && tree.get("categories").isArray();
    }

    private static boolean validCached(
            CachedProviderResult cached, String expectedProvider, int timeoutMs) {
        if (cached == null || expectedProvider == null || expectedProvider.isBlank()
                || timeoutMs <= 0 || !expectedProvider.equals(cached.provider())
                || cached.decision() == null
                || cached.decision() != ProviderDecision.PASS
                && cached.decision() != ProviderDecision.HIGH_RISK
                || cached.riskScore() == null || cached.riskScore() < 0
                || cached.riskScore() > 100 || cached.categories() == null
                || cached.providerRequestId() == null
                || cached.providerRequestId().isBlank()
                || cached.providerRequestId().length() > 128
                || cached.latencyMs() == null || cached.latencyMs() < 0
                || cached.latencyMs() > 60_000) {
            return false;
        }
        return cached.categories().stream().allMatch(category ->
                category != null && category.matches(SAFE_CODE_PATTERN));
    }

    private static boolean positiveAndBoundedCost(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        try {
            return costUnitsCeiling(amount).length() <= MAX_COUNTER_DIGITS;
        } catch (ArithmeticException invalid) {
            return false;
        }
    }

    private static boolean boundedBudget(BigDecimal amount) {
        try {
            return budgetUnitsFloor(amount).length() <= MAX_COUNTER_DIGITS;
        } catch (ArithmeticException invalid) {
            return false;
        }
    }

    private static String costUnitsCeiling(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.CEILING)
                .movePointRight(MONEY_SCALE).toBigIntegerExact().toString();
    }

    private static String budgetUnitsFloor(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.FLOOR)
                .movePointRight(MONEY_SCALE).toBigIntegerExact().toString();
    }

    public enum AcquireOutcome {
        ACQUIRED, DAILY_LIMIT, MONTHLY_BUDGET, BUDGET_DISABLED, REDIS_UNAVAILABLE
    }

    public enum CacheOutcome { HIT, MISS, REDIS_UNAVAILABLE }

    public record Acquisition(AcquireOutcome outcome) { }

    public record CacheLookup(CacheOutcome outcome, Optional<ProviderResult> result) { }

    private record CachedProviderResult(
            String provider, ProviderDecision decision, Integer riskScore,
            Set<String> categories, String providerRequestId, Long latencyMs) { }

    public interface ProviderStateStore {
        String get(String key);
        void put(String key, String value, Duration ttl);
        AcquireOutcome acquireBudget(
                String dailyKey, int dailyLimit, Duration dailyTtl,
                String monthlyKey, String monthlyBudgetUnits,
                String estimatedCostUnits, Duration monthlyTtl);
    }

    /** RedisTemplate implementation; the Lua result proves both counters move atomically. */
    public static final class RedisProviderStateStore implements ProviderStateStore {
        public static final String BUDGET_LUA = """
                local MAX_DIGITS = 30
                local function valid(value)
                  return type(value) == 'string' and value:match('^%d+$') ~= nil
                    and string.len(value) <= MAX_DIGITS
                end
                local function strip(value)
                  local normalized = string.gsub(value, '^0+', '')
                  if normalized == '' then return '0' end
                  return normalized
                end
                local function compare(left, right)
                  left = strip(left)
                  right = strip(right)
                  if string.len(left) < string.len(right) then return -1 end
                  if string.len(left) > string.len(right) then return 1 end
                  if left < right then return -1 end
                  if left > right then return 1 end
                  return 0
                end
                local function add(left, right)
                  local carry = 0
                  local result = ''
                  local i = string.len(left)
                  local j = string.len(right)
                  while i > 0 or j > 0 or carry > 0 do
                    local a = i > 0 and string.byte(left, i) - 48 or 0
                    local b = j > 0 and string.byte(right, j) - 48 or 0
                    local sum = a + b + carry
                    result = string.char((sum % 10) + 48) .. result
                    carry = math.floor(sum / 10)
                    i = i - 1
                    j = j - 1
                  end
                  return strip(result)
                end
                local dailyRaw = redis.call('GET', KEYS[1])
                local monthlyRaw = redis.call('GET', KEYS[2])
                if (dailyRaw and not valid(dailyRaw)) or (monthlyRaw and not valid(monthlyRaw)) then
                  return 4
                end
                for i = 1, 5 do
                  if not valid(ARGV[i]) then return 4 end
                end
                local dailyUsed = strip(dailyRaw or '0')
                local monthlyUsed = strip(monthlyRaw or '0')
                local dailyLimit = strip(ARGV[1])
                local monthlyLimit = strip(ARGV[2])
                local cost = strip(ARGV[3])
                if compare(dailyLimit, '0') <= 0 or compare(monthlyLimit, '0') <= 0
                    or compare(cost, '0') <= 0 then return 4 end
                local newDaily = add(dailyUsed, '1')
                local newMonthly = add(monthlyUsed, cost)
                if compare(newDaily, dailyLimit) > 0 then return 2 end
                if compare(newMonthly, monthlyLimit) > 0 then return 3 end
                if string.len(newDaily) > MAX_DIGITS or string.len(newMonthly) > MAX_DIGITS then
                  return 4
                end
                local dailyPttl = redis.call('PTTL', KEYS[1])
                local monthlyPttl = redis.call('PTTL', KEYS[2])
                redis.call('SET', KEYS[1], newDaily)
                redis.call('SET', KEYS[2], newMonthly)
                if dailyPttl > 0 then redis.call('PEXPIRE', KEYS[1], dailyPttl)
                else redis.call('EXPIRE', KEYS[1], ARGV[4]) end
                if monthlyPttl > 0 then redis.call('PEXPIRE', KEYS[2], monthlyPttl)
                else redis.call('EXPIRE', KEYS[2], ARGV[5]) end
                return 1
                """;
        private final StringRedisTemplate redisTemplate;
        private final DefaultRedisScript<Long> script =
                new DefaultRedisScript<>(BUDGET_LUA, Long.class);

        public RedisProviderStateStore(StringRedisTemplate redisTemplate) {
            this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        }

        @Override
        public String get(String key) {
            return redisTemplate.opsForValue().get(key);
        }

        @Override
        public void put(String key, String value, Duration ttl) {
            redisTemplate.opsForValue().set(key, value, ttl.toSeconds(), TimeUnit.SECONDS);
        }

        @Override
        public AcquireOutcome acquireBudget(
                String dailyKey, int dailyLimit, Duration dailyTtl,
                String monthlyKey, String monthlyBudgetUnits,
                String estimatedCostUnits, Duration monthlyTtl) {
            Long code = redisTemplate.execute(script, List.of(dailyKey, monthlyKey),
                    Integer.toString(dailyLimit), monthlyBudgetUnits,
                    estimatedCostUnits,
                    Long.toString(dailyTtl.toSeconds()), Long.toString(monthlyTtl.toSeconds()));
            if (Long.valueOf(1).equals(code)) return AcquireOutcome.ACQUIRED;
            if (Long.valueOf(2).equals(code)) return AcquireOutcome.DAILY_LIMIT;
            if (Long.valueOf(3).equals(code)) return AcquireOutcome.MONTHLY_BUDGET;
            if (Long.valueOf(4).equals(code)) return AcquireOutcome.REDIS_UNAVAILABLE;
            throw new IllegalStateException("Unexpected Redis budget result");
        }
    }
}
