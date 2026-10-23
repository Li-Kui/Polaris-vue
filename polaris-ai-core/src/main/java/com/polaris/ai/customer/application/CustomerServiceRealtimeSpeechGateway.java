package com.polaris.ai.customer.application;

import com.polaris.ai.runtime.usage.NormalizedUsage;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 实时识别只产生字幕和待核对的最终文本，不驱动业务操作。 */
public interface CustomerServiceRealtimeSpeechGateway {
    boolean supports(Long modelId);
    Session open(Long modelId, String hash, BooleanSupplier active, Consumer<String> partial, Consumer<NormalizedUsage> usage);
    default Session open(Long modelId, String hash, List<String> vocabulary, BooleanSupplier active, Consumer<String> partial, Consumer<NormalizedUsage> usage) {
        if (vocabulary != null && !vocabulary.isEmpty()) throw new com.polaris.common.exception.ServiceException("SPEECH_VOCABULARY_UNSUPPORTED: 当前实时模型不支持术语提示",400);
        return open(modelId,hash,active,partial,usage);
    }
    interface Session extends AutoCloseable {
        void audio(byte[] pcm);
        String finish();
        @Override void close();
    }
}
