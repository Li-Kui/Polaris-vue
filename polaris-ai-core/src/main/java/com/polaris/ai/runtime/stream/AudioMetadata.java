package com.polaris.ai.runtime.stream;

/** 音频流元数据。 */
public record AudioMetadata(
        String contentType,
        Integer sampleRateHz,
        Integer channels) implements ModelStreamEvent {
}
