package com.polaris.ai.safety.provider;

import com.polaris.ai.safety.dto.ProviderResult;
import com.polaris.ai.safety.model.ModerationScene;

/** Optional external moderation boundary. */
public interface ModerationProvider {
    ProviderResult review(ModerationScene scene, String text, String requestId);

    default ProviderResult review(
            ModerationScene scene, String text, String requestId, int timeoutMs) {
        return review(scene, text, requestId);
    }

    boolean configured();

    default String name() {
        return "unknown";
    }
}
