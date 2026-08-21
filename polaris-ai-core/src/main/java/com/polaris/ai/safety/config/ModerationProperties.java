package com.polaris.ai.safety.config;

import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.model.PolicyMode;
import com.polaris.ai.safety.model.PolicyPreset;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/** Local moderation defaults used when a scene has no enabled persisted policy. */
@ConfigurationProperties(prefix = "ai.moderation")
@Component
public class ModerationProperties {
    private String storageRoot;
    private final Map<ModerationScene, ScenePolicy> scenes = defaults();
    private int auditRetentionDays = 90;
    private int candidateObservationThreshold = 3;

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public Map<ModerationScene, ScenePolicy> getScenes() {
        return scenes;
    }

    public int getAuditRetentionDays() {
        return auditRetentionDays;
    }

    public void setAuditRetentionDays(int auditRetentionDays) {
        this.auditRetentionDays = auditRetentionDays;
    }

    public int getCandidateObservationThreshold() {
        return candidateObservationThreshold;
    }

    public void setCandidateObservationThreshold(int candidateObservationThreshold) {
        this.candidateObservationThreshold = candidateObservationThreshold;
    }

    private static Map<ModerationScene, ScenePolicy> defaults() {
        Map<ModerationScene, ScenePolicy> result = new EnumMap<>(ModerationScene.class);
        result.put(ModerationScene.KNOWLEDGE, scene(PolicyPreset.LENIENT));
        result.put(ModerationScene.CHAT_INPUT, scene(PolicyPreset.BALANCED));
        result.put(ModerationScene.AI_OUTPUT, scene(PolicyPreset.STRICT));
        result.put(ModerationScene.WORKFLOW_INPUT, scene(PolicyPreset.BALANCED));
        result.put(ModerationScene.WORKFLOW_OUTPUT, scene(PolicyPreset.STRICT));
        return result;
    }

    private static ScenePolicy scene(PolicyPreset preset) {
        ScenePolicy policy = new ScenePolicy();
        policy.setPreset(preset);
        return policy;
    }

    /** Bindable per-scene policy defaults; no provider credentials are stored here. */
    public static class ScenePolicy {
        private PolicyPreset preset;
        private PolicyMode mode = PolicyMode.ENFORCE;
        private boolean enabled = true;
        private int suspectThreshold = 40;
        private int blockThreshold = 70;
        private boolean providerEnabled;
        private int providerTimeoutMs = 1500;
        private int providerDailyLimit = 1000;
        private BigDecimal providerMonthlyBudget = new BigDecimal("100.00");
        private int segmentChars = 200;
        private int segmentOverlapChars = 64;
        private int outputBufferChars = 300;
        private int quarantineDays = 7;
        private long policyVersion = 1L;

        public PolicyPreset getPreset() {
            return preset;
        }

        public void setPreset(PolicyPreset preset) {
            this.preset = preset;
        }

        public PolicyMode getMode() {
            return mode;
        }

        public void setMode(PolicyMode mode) {
            this.mode = mode;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getSuspectThreshold() {
            return suspectThreshold;
        }

        public void setSuspectThreshold(int suspectThreshold) {
            this.suspectThreshold = suspectThreshold;
        }

        public int getBlockThreshold() {
            return blockThreshold;
        }

        public void setBlockThreshold(int blockThreshold) {
            this.blockThreshold = blockThreshold;
        }

        public boolean isProviderEnabled() {
            return providerEnabled;
        }

        public void setProviderEnabled(boolean providerEnabled) {
            this.providerEnabled = providerEnabled;
        }

        public int getProviderTimeoutMs() {
            return providerTimeoutMs;
        }

        public void setProviderTimeoutMs(int providerTimeoutMs) {
            this.providerTimeoutMs = providerTimeoutMs;
        }

        public int getProviderDailyLimit() {
            return providerDailyLimit;
        }

        public void setProviderDailyLimit(int providerDailyLimit) {
            this.providerDailyLimit = providerDailyLimit;
        }

        public BigDecimal getProviderMonthlyBudget() {
            return providerMonthlyBudget;
        }

        public void setProviderMonthlyBudget(BigDecimal providerMonthlyBudget) {
            this.providerMonthlyBudget = providerMonthlyBudget;
        }

        public int getSegmentChars() {
            return segmentChars;
        }

        public void setSegmentChars(int segmentChars) {
            this.segmentChars = segmentChars;
        }

        public int getSegmentOverlapChars() {
            return segmentOverlapChars;
        }

        public void setSegmentOverlapChars(int segmentOverlapChars) {
            this.segmentOverlapChars = segmentOverlapChars;
        }

        public int getOutputBufferChars() {
            return outputBufferChars;
        }

        public void setOutputBufferChars(int outputBufferChars) {
            this.outputBufferChars = outputBufferChars;
        }

        public int getQuarantineDays() {
            return quarantineDays;
        }

        public void setQuarantineDays(int quarantineDays) {
            this.quarantineDays = quarantineDays;
        }

        public long getPolicyVersion() {
            return policyVersion;
        }

        public void setPolicyVersion(long policyVersion) {
            this.policyVersion = policyVersion;
        }
    }
}
