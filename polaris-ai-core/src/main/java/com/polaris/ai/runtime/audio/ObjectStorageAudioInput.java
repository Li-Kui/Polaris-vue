package com.polaris.ai.runtime.audio;

import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

public record ObjectStorageAudioInput(
        String objectKey,
        Supplier<InputStream> streamSupplier,
        String fileName,
        String contentType,
        Long contentLength) implements AudioInput {

    public ObjectStorageAudioInput {
        objectKey = Objects.requireNonNull(objectKey, "objectKey");
        streamSupplier = Objects.requireNonNull(streamSupplier, "streamSupplier");
        fileName = fileName == null ? "audio.bin" : fileName;
        contentType = contentType == null ? "application/octet-stream" : contentType;
    }

    @Override public InputStream openStream() { return streamSupplier.get(); }
    @Override public String toString() { return "ObjectStorageAudioInput[objectKey=" + objectKey + ", stream=<redacted>]"; }
}
