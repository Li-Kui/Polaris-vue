package com.polaris.ai.customer.application;

import com.polaris.ai.runtime.audio.RecognitionVocabulary;
import com.polaris.common.exception.ServiceException;

import java.util.List;

/** 识别、供应商合成与设备本地播报分别配置；发布后固定模型定义。 */
public record CustomerServiceVoiceConfig(Long sttModelId, Long ttsModelId, boolean devicePlayback,
        List<String> vocabulary, Boolean briefReplies) {
    public CustomerServiceVoiceConfig(Long sttModelId, Long ttsModelId, boolean devicePlayback) { this(sttModelId, ttsModelId, devicePlayback, List.of(), false); }
    public CustomerServiceVoiceConfig(Long sttModelId, boolean devicePlayback) { this(sttModelId, null, devicePlayback); }
    public CustomerServiceVoiceConfig {
        if (sttModelId != null && sttModelId <= 0 || ttsModelId != null && ttsModelId <= 0) throw new ServiceException("INVALID_INPUT: 语音模型无效", 400);
        vocabulary = RecognitionVocabulary.normalize(vocabulary);
        // 包装类型兼容旧快照缺少该字段，构造后始终归一为非空值。
        briefReplies = Boolean.TRUE.equals(briefReplies);
        if (!vocabulary.isEmpty() && sttModelId == null) throw new ServiceException("INVALID_INPUT: 配置识别术语前请选择识别模型",400);
    }
}
