package com.polaris.ai.runtime.audio;

import java.io.InputStream;

/** 可流式读取的音频输入，避免统一强制装载到 JVM 堆内存。 */
public interface AudioInput {

    InputStream openStream();

    String fileName();

    String contentType();

    Long contentLength();
}
