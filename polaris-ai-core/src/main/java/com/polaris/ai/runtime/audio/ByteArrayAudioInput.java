package com.polaris.ai.runtime.audio;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public final class ByteArrayAudioInput implements AudioInput {

    private final byte[] data;
    private final String fileName;
    private final String contentType;

    public ByteArrayAudioInput(byte[] data, String fileName, String contentType) {
        this.data = data == null ? new byte[0] : data.clone();
        this.fileName = fileName == null ? "audio.bin" : fileName;
        this.contentType = contentType == null
                ? "application/octet-stream" : contentType;
    }

    @Override public InputStream openStream() { return new ByteArrayInputStream(data); }
    @Override public String fileName() { return fileName; }
    @Override public String contentType() { return contentType; }
    @Override public Long contentLength() { return (long) data.length; }
    @Override public String toString() { return "ByteArrayAudioInput[data=<redacted>, size=" + data.length + "]"; }
}
