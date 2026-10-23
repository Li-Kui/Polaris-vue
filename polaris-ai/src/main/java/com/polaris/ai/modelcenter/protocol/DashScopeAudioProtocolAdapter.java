package com.polaris.ai.modelcenter.protocol;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** 原生语音固定路径；豆包识别的 GET 映射表示 WebSocket 握手。 */
@Component
public class DashScopeAudioProtocolAdapter implements ProtocolAdapter {
    @Override public String protocolCode() { return "DASHSCOPE_AUDIO"; }
    @Override public int adapterVersion() { return 1; }
    @Override public Set<String> mappedCapabilities() { return Set.of("AUDIO_STT", "AUDIO_TTS"); }
    @Override public Optional<ProtocolEndpoint> findEndpoint(String capability) {
        return switch (capability == null ? "" : capability.toUpperCase(Locale.ROOT)) {
            case "AUDIO_STT" -> Optional.of(new ProtocolEndpoint(ProtocolHttpMethod.POST, "/api/v1/services/aigc/multimodal-generation/generation"));
            case "AUDIO_TTS" -> Optional.of(new ProtocolEndpoint(ProtocolHttpMethod.POST, "/api/v1/services/aigc/multimodal-generation/generation"));
            default -> Optional.empty();
        };
    }
    @Override public Optional<ProtocolEndpoint> modelDiscoveryEndpoint() { return Optional.empty(); }
    @Override public Optional<ProtocolEndpoint> findEndpoint(String capability, String modelName) {
        // 新增模型映射；既有模型路径及 adapterVersion 保持不变，避免改变原发布哈希。
        if ("AUDIO_TTS".equalsIgnoreCase(capability)
                && com.polaris.ai.modelcenter.audio.DashScopeSpeechModels.qwenAudioTts(modelName)) {
            return Optional.of(new ProtocolEndpoint(ProtocolHttpMethod.POST, "/api/v1/services/audio/tts/SpeechSynthesizer"));
        }
        return findEndpoint(capability);
    }
}
