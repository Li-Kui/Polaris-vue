package com.polaris.ai.customer.application;

import com.polaris.ai.runtime.usage.NormalizedUsage;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 客服短录音识别边界；不接收任意供应商地址、覆盖参数或外部音频 URL。 */
public interface CustomerServiceSpeechGateway {
    List<Model> models();
    String validate(Long modelId);
    String transcribe(Long modelId, String modelHash, byte[] wav, BooleanSupplier active, Consumer<NormalizedUsage> usage);
    default void validateVocabulary(Long modelId, List<String> vocabulary) {
        if (vocabulary != null && !vocabulary.isEmpty()) throw new com.polaris.common.exception.ServiceException("SPEECH_VOCABULARY_UNSUPPORTED: 当前识别模型不支持术语提示",400);
    }
    default String transcribe(Long modelId, String modelHash, byte[] wav, List<String> vocabulary, BooleanSupplier active, Consumer<NormalizedUsage> usage) {
        validateVocabulary(modelId,vocabulary); return transcribe(modelId,modelHash,wav,active,usage);
    }
    String validateTts(Long modelId);
    com.polaris.ai.runtime.audio.AudioTtsResult synthesize(Long modelId, String modelHash, String text,
            BooleanSupplier active, Consumer<NormalizedUsage> usage);
    record Model(Long id, String name, String capability) {
        public Model(Long id, String name) { this(id, name, "AUDIO_STT"); }
    }
}
