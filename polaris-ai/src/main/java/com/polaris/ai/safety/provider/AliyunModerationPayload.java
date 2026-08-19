package com.polaris.ai.safety.provider;

/** Minimal, privacy-safe projection of the Aliyun SDK response. */
import java.util.List;

public record AliyunModerationPayload(
        Integer statusCode,
        Integer bodyCode,
        String requestId,
        List<AliyunLabelResult> results) {

    public AliyunModerationPayload {
        results = results == null ? null : List.copyOf(results);
    }
}
