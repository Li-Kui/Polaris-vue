package com.polaris.ai.runtime.audio;

public record AudioTranscriptSegment(
        long startMillis,
        long endMillis,
        String speaker,
        String text) {
}
