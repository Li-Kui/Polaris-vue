package com.polaris.ai.runtime.audio;

public record AudioTtsResult(byte[] audio, String contentType, Integer sampleRate) {
    public AudioTtsResult { audio = audio == null ? new byte[0] : audio.clone(); }
    @Override public byte[] audio() { return audio.clone(); }
    @Override public String toString() { return "AudioTtsResult[audio=<redacted>, size=" + audio.length + ", contentType=" + contentType + "]"; }
}
