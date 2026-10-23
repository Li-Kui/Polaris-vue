package com.polaris.ai.runtime.audio;

import com.polaris.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.List;

/** 有界实体词表，只用于识别输入提示，不提供替换规则或模型指令。 */
public final class RecognitionVocabulary {
    public static List<String> normalize(List<?> values) {
        if (values == null) return List.of();
        if (values.size() > 20) throw invalid();
        List<String> result = new ArrayList<>();
        int chars = 0;
        for (Object item : values) {
            if (!(item instanceof String text)) throw invalid();
            String word = text.strip();
            if (!word.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} ._+/-]{0,29}") || result.contains(word)) throw invalid();
            chars += word.length();
            if (chars > 300) throw invalid();
            result.add(word);
        }
        return List.copyOf(result);
    }
    public static boolean supported(String protocol, String model) {
        return model != null && ("DOUBAO_SPEECH".equals(protocol) && model.matches("volc\\.(seedasr|bigasr)\\.sauc\\.(duration|concurrent)")
                || "DASHSCOPE_AUDIO".equals(protocol) && model.matches("qwen3-asr-flash(?:-\\d{4}-\\d{2}-\\d{2})?"));
    }
    private static ServiceException invalid() { return new ServiceException("INVALID_INPUT: 识别术语最多20项、每项30字、合计300字，不能重复或包含控制符及指令标点",400); }
    private RecognitionVocabulary() { }
}
