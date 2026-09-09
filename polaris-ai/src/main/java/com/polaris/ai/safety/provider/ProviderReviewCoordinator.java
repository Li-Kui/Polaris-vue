package com.polaris.ai.safety.provider;

import com.polaris.ai.safety.dto.ProviderResult;
import com.polaris.ai.safety.model.ModerationPolicy;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.model.ProviderDecision;
import com.polaris.ai.safety.rule.TextNormalizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Orchestrates cache, circuit, budget, and the single eligible provider call. */
public final class ProviderReviewCoordinator {
    public static final String PROVIDER_NOT_CONFIGURED = "PROVIDER_NOT_CONFIGURED";
    public static final String REDIS_UNAVAILABLE = "REDIS_UNAVAILABLE";
    public static final String PROVIDER_BUDGET_UNAVAILABLE = "PROVIDER_BUDGET_UNAVAILABLE";
    public static final String CIRCUIT_OPEN = "CIRCUIT_OPEN";
    public static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";

    private final ModerationProvider provider;
    private final ProviderUsageGuard guard;
    private final ProviderCircuitBreaker circuitBreaker;
    private final TextNormalizer normalizer = new TextNormalizer();

    public ProviderReviewCoordinator(
            ModerationProvider provider, ProviderUsageGuard guard,
            ProviderCircuitBreaker circuitBreaker) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.guard = guard;
        this.circuitBreaker = circuitBreaker;
    }

    public static ProviderReviewCoordinator notConfigured() {
        return new ProviderReviewCoordinator(new NoopModerationProvider(), null, null);
    }

    public Review review(ModerationPolicy policy, ModerationScene scene,
                         String text, String requestId) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(text, "text");
        if (!Boolean.TRUE.equals(policy.getProviderEnabled())) {
            return new Review(null, null);
        }
        if (!provider.configured()) {
            return new Review(null, PROVIDER_NOT_CONFIGURED);
        }

        String hash = sha256(normalizer.normalize(text).text());
        ProviderUsageGuard.CacheLookup cached = guard.lookup(
                policy, scene, hash, provider.name(), policy.getProviderTimeoutMs());
        if (cached.outcome() == ProviderUsageGuard.CacheOutcome.REDIS_UNAVAILABLE) {
            return new Review(null, REDIS_UNAVAILABLE);
        }
        if (cached.result().isPresent()) {
            return new Review(cached.result().orElseThrow(), null);
        }
        ProviderCircuitBreaker.Permission permission =
                circuitBreaker.acquirePermission();
        if (!permission.permitted()) {
            return new Review(null, CIRCUIT_OPEN);
        }

        ProviderUsageGuard.Acquisition acquisition = guard.acquire(policy, hash);
        if (acquisition.outcome() != ProviderUsageGuard.AcquireOutcome.ACQUIRED) {
            circuitBreaker.release(permission);
            String reason = acquisition.outcome() == ProviderUsageGuard.AcquireOutcome.REDIS_UNAVAILABLE
                    ? REDIS_UNAVAILABLE : PROVIDER_BUDGET_UNAVAILABLE;
            return new Review(null, reason);
        }

        ProviderResult result;
        try {
            result = provider.review(
                    scene, text, requestId, policy.getProviderTimeoutMs());
        } catch (RuntimeException providerFailure) {
            circuitBreaker.recordFailure(permission);
            return new Review(null, PROVIDER_UNAVAILABLE);
        }
        if (result == null || result.decision() == null) {
            circuitBreaker.recordFailure(permission);
            return new Review(null, PROVIDER_UNAVAILABLE);
        }
        if (result.decision() == ProviderDecision.UNAVAILABLE) {
            circuitBreaker.recordFailure(permission);
            return new Review(result, PROVIDER_UNAVAILABLE);
        }
        circuitBreaker.recordSuccess(permission);
        guard.cache(policy, scene, hash, result);
        return new Review(result, null);
    }

    private static String sha256(String normalizedText) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK SHA-256 is unavailable", impossible);
        }
    }

    public record Review(ProviderResult providerResult, String fallbackReason) { }
}
