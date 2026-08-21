package com.polaris.ai.safety.dto;

import com.polaris.ai.safety.model.ModerationScene;

import java.util.UUID;

/** Immutable internal request; the submitted text is never included in result DTOs. */
public record ModerationRequest(
        ModerationScene scene,
        String text,
        String requestId,
        String resourceType,
        String resourceId,
        Long dictionaryVersion) {

    public ModerationRequest {
        if (scene == null || text == null) {
            throw new IllegalArgumentException("检测场景和文本不能为空");
        }
    }

    public static ModerationRequest of(
            ModerationScene scene, String text, String resourceType, String resourceId) {
        if (scene == null || text == null || text.isBlank()) {
            throw new IllegalArgumentException("检测场景和文本不能为空");
        }
        return new ModerationRequest(scene, text, UUID.randomUUID().toString(),
                resourceType, resourceId, null);
    }

    public static ModerationRequest forStream(
            ModerationScene scene, String resourceType, String resourceId) {
        return of(scene, "stream", resourceType, resourceId);
    }

    public ModerationRequest withDictionaryVersion(long version) {
        return new ModerationRequest(scene, text, requestId,
                resourceType, resourceId, version);
    }
}
