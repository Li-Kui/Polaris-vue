package com.polaris.ai.safety.provider;

import com.polaris.ai.safety.dto.ProviderResult;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.model.ProviderDecision;

import java.util.Set;

/** Safe provider used when no supported provider has complete credentials. */
public final class NoopModerationProvider implements ModerationProvider {
    @Override
    public ProviderResult review(ModerationScene scene, String text, String requestId) {
        return new ProviderResult("none", ProviderDecision.UNAVAILABLE,
                null, Set.of(), null, 0L);
    }

    @Override
    public boolean configured() {
        return false;
    }

    @Override
    public String name() {
        return "none";
    }
}
