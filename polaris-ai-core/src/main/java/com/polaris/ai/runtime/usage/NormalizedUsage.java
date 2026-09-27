package com.polaris.ai.runtime.usage;

import java.util.Map;

/** 跨 Provider 的统一 Usage；未知值保持 null。 */
public record NormalizedUsage(
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Long inputCharacters,
        Long audioInputMillis,
        Long audioOutputMillis,
        Map<String, Object> details,
        UsageSource source) {

    public NormalizedUsage {
        requireNonNegative(inputTokens, "inputTokens");
        requireNonNegative(outputTokens, "outputTokens");
        requireNonNegative(totalTokens, "totalTokens");
        requireNonNegative(inputCharacters, "inputCharacters");
        requireNonNegative(audioInputMillis, "audioInputMillis");
        requireNonNegative(audioOutputMillis, "audioOutputMillis");
        details = details == null ? Map.of() : Map.copyOf(details);
        source = source == null ? UsageSource.UNKNOWN : source;
    }

    public static NormalizedUsage unknown() {
        return new NormalizedUsage(
                null, null, null, null, null, null,
                Map.of(), UsageSource.UNKNOWN);
    }

    @Override
    public String toString() {
        return "NormalizedUsage[inputTokens=" + inputTokens
                + ", outputTokens=" + outputTokens
                + ", totalTokens=" + totalTokens
                + ", inputCharacters=" + inputCharacters
                + ", audioInputMillis=" + audioInputMillis
                + ", audioOutputMillis=" + audioOutputMillis
                + ", detailKeys=" + details.keySet()
                + ", source=" + source + "]";
    }

    private static void requireNonNegative(Number value, String field) {
        if (value != null && value.longValue() < 0) {
            throw new IllegalArgumentException(field + " 不能为负数");
        }
    }
}
