package com.polaris.ai.runtime.audio;

import java.util.List;

public record AudioSttResult(String text, List<AudioTranscriptSegment> segments) {
    public AudioSttResult { segments = segments == null ? List.of() : List.copyOf(segments); }
}
