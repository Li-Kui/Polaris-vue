package com.polaris.ai.safety.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.polaris.ai.safety.config.ModerationProperties;
import com.polaris.ai.safety.exception.ModerationUnavailableException;
import com.polaris.ai.safety.mapper.ModerationPolicyMapper;
import com.polaris.ai.safety.model.ModerationPolicy;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.model.PolicyMode;
import com.polaris.ai.safety.model.PolicyPreset;
import com.polaris.ai.safety.service.IModerationPolicyService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * AI 安全策略解析服务层实现类
 *
 * @author polaris
 */
@Service
public class ModerationPolicyServiceImpl implements IModerationPolicyService {

    @Autowired(required = false)
    private ModerationPolicyMapper policyMapper;

    @Autowired(required = false)
    private ModerationProperties properties;

    private PolicyLookup policyLookup;

    public ModerationPolicyServiceImpl() {}

    public ModerationPolicyServiceImpl(PolicyLookup policyLookup, ModerationProperties properties) {
        this.policyLookup = policyLookup;
        this.properties = properties;
    }

    public ModerationPolicyServiceImpl(ModerationPolicyMapper policyMapper, ModerationProperties properties) {
        this.policyMapper = policyMapper;
        this.properties = properties;
        if (policyMapper != null) {
            this.policyLookup = scene -> policyMapper.selectOne(
                    Wrappers.<ModerationPolicy>query()
                            .eq("scene", scene.name())
                            .eq("enabled", true)
                            .last("LIMIT 1"));
        }
    }

    @PostConstruct
    public void init() {
        if (this.policyLookup == null && this.policyMapper != null) {
            this.policyLookup = scene -> policyMapper.selectOne(
                    Wrappers.<ModerationPolicy>query()
                            .eq("scene", scene.name())
                            .eq("enabled", true)
                            .last("LIMIT 1"));
        }
    }

    public void setPolicyLookup(PolicyLookup policyLookup) {
        this.policyLookup = policyLookup;
    }

    public void setProperties(ModerationProperties properties) {
        this.properties = properties;
    }

    @Override
    public ModerationPolicy resolve(ModerationScene scene) {
        Objects.requireNonNull(scene, "scene");
        ModerationPolicy persisted = null;
        if (policyLookup != null) {
            try {
                persisted = policyLookup.findEnabledByScene(scene);
            } catch (RuntimeException lookupFailure) {
                throw unavailable("Moderation policy lookup failed", lookupFailure);
            }
        }
        ModerationPolicy resolved = persisted == null ? builtIn(scene) : persisted;
        validate(scene, resolved);
        return resolved;
    }

    private ModerationPolicy builtIn(ModerationScene scene) {
        if (properties == null || properties.getScenes() == null) {
            throw unavailable("No moderation defaults configured for " + scene, null);
        }
        ModerationProperties.ScenePolicy source = properties.getScenes().get(scene);
        if (source == null) {
            throw unavailable("No moderation defaults configured for " + scene, null);
        }
        ModerationPolicy policy = new ModerationPolicy();
        policy.setScene(scene.name());
        policy.setPreset(source.getPreset() == null ? null : source.getPreset().name());
        policy.setMode(source.getMode() == null ? null : source.getMode().name());
        policy.setEnabled(source.isEnabled());
        policy.setSuspectThreshold(source.getSuspectThreshold());
        policy.setBlockThreshold(source.getBlockThreshold());
        policy.setProviderEnabled(source.isProviderEnabled());
        policy.setProviderTimeoutMs(source.getProviderTimeoutMs());
        policy.setProviderDailyLimit(source.getProviderDailyLimit());
        policy.setProviderMonthlyBudget(source.getProviderMonthlyBudget());
        policy.setSegmentChars(source.getSegmentChars());
        policy.setSegmentOverlapChars(source.getSegmentOverlapChars());
        policy.setOutputBufferChars(source.getOutputBufferChars());
        policy.setQuarantineDays(source.getQuarantineDays());
        policy.setPolicyVersion(source.getPolicyVersion());
        return policy;
    }

    private void validate(ModerationScene expectedScene, ModerationPolicy policy) {
        if (policy == null || !expectedScene.name().equals(policy.getScene())) {
            throw unavailable("Moderation policy scene does not match the request", null);
        }
        enumValue(PolicyPreset.class, policy.getPreset(), "preset");
        enumValue(PolicyMode.class, policy.getMode(), "mode");
        if (!Boolean.TRUE.equals(policy.getEnabled())) {
            throw unavailable("Moderation policy must be enabled", null);
        }
        Integer suspect = policy.getSuspectThreshold();
        Integer block = policy.getBlockThreshold();
        if (suspect == null || block == null || suspect < 0 || suspect >= block || block > 100) {
            throw unavailable("Moderation policy thresholds are invalid", null);
        }
        if (policy.getPolicyVersion() == null || policy.getPolicyVersion() <= 0) {
            throw unavailable("Moderation policy version must be positive", null);
        }
        if (!between(policy.getQuarantineDays(), 1, 90)) {
            throw unavailable("Moderation quarantine days are invalid", null);
        }
        if (!positive(policy.getSegmentChars())
                || !positive(policy.getSegmentOverlapChars())
                || !positive(policy.getOutputBufferChars())
                || policy.getSegmentOverlapChars() > policy.getSegmentChars()
                || policy.getSegmentOverlapChars() > policy.getOutputBufferChars()) {
            throw unavailable("Moderation segment and buffer settings are invalid", null);
        }
        if (policy.getProviderEnabled() == null
                || !between(policy.getProviderTimeoutMs(), 100, 10_000)
                || policy.getProviderDailyLimit() == null
                || policy.getProviderDailyLimit() < 0
                || policy.getProviderMonthlyBudget() == null
                || policy.getProviderMonthlyBudget().compareTo(BigDecimal.ZERO) < 0) {
            throw unavailable("Moderation provider limits are invalid", null);
        }
    }

    private static <E extends Enum<E>> void enumValue(
            Class<E> type, String value, String field) {
        try {
            Enum.valueOf(type, value);
        } catch (NullPointerException | IllegalArgumentException invalid) {
            throw unavailable("Moderation policy " + field + " is invalid", invalid);
        }
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private static boolean between(Integer value, int minimum, int maximum) {
        return value != null && value >= minimum && value <= maximum;
    }

    private static ModerationUnavailableException unavailable(
            String message, Throwable cause) {
        return new ModerationUnavailableException(message, cause);
    }

    @FunctionalInterface
    public interface PolicyLookup {
        ModerationPolicy findEnabledByScene(ModerationScene scene);
    }
}
