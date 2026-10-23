package com.polaris.ai.runtime.audio;

import com.polaris.ai.runtime.CapabilityInvocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AudioSttInvocation(
        AudioInput audio,
        Map<String, Object> overrides, List<String> vocabulary) implements CapabilityInvocation {
    public AudioSttInvocation(AudioInput audio, Map<String,Object> overrides) { this(audio, overrides, List.of()); }
    public AudioSttInvocation {
        audio = Objects.requireNonNull(audio, "audio");
        overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
        vocabulary = RecognitionVocabulary.normalize(vocabulary);
    }
    @Override public String capabilityCode() { return "AUDIO_STT"; }
    @Override public String toString() { return "AudioSttInvocation[audio=<redacted>]"; }
}
