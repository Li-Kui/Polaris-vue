package com.polaris.ai.modelcenter.audio;

import java.util.List;

/** 百炼语音模型的明确支持范围；不同代际不能混用端点和音色。 */
public final class DashScopeSpeechModels {
    private static final List<String> QWEN_AUDIO_VOICES = List.of(
            "longanhuan_v3.1", "longanlingxin_v3.1", "longanfengyue_v3.1", "xunanchuan_v3.1");
    public static boolean qwenAudioTts(String model) { return "qwen-audio-3.1-tts-flash".equals(model); }
    public static boolean supportsTts(String model) {
        return qwenAudioTts(model) || model != null && model.matches("qwen3-tts-flash(?:-\\d{4}-\\d{2}-\\d{2})?");
    }
    public static List<String> qwenAudioVoices() { return QWEN_AUDIO_VOICES; }
    private DashScopeSpeechModels() { }
}
