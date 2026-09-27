package com.polaris.ai.runtime.audio;

import com.polaris.ai.runtime.CapabilityInvocation;

import java.util.Map;
import java.util.Objects;

public record AudioTtsInvocation(
        String text,
        boolean stream,
        Map<String, Object> overrides) implements CapabilityInvocation {

    public AudioTtsInvocation {
        text = Objects.requireNonNull(text, "text");
        overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
    }
    @Override public String capabilityCode() { return "AUDIO_TTS"; }
    @Override public String toString() { return "AudioTtsInvocation[text=<redacted>, characters=" + text.length() + ", stream=" + stream + "]"; }
}
