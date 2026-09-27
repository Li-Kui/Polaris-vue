package com.polaris.ai.runtime.stream;

/** 音频二进制增量；日志表示不包含正文。 */
public record AudioChunk(byte[] data) implements ModelStreamEvent {

    public AudioChunk {
        data = data == null ? new byte[0] : data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public String toString() {
        return "AudioChunk[data=<redacted>, size=" + data.length + "]";
    }
}
