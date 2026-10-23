package com.polaris.ai.modelcenter.protocol;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** 原生语音固定路径；豆包识别的 WEBSOCKET 映射表示 WebSocket 握手。 */
@Component
public class DoubaoSpeechProtocolAdapter implements ProtocolAdapter {
    @Override public String protocolCode() { return "DOUBAO_SPEECH"; }
    @Override public int adapterVersion() { return 1; }
    @Override public Set<String> mappedCapabilities() { return Set.of("AUDIO_STT", "AUDIO_TTS"); }
    @Override public Optional<ProtocolEndpoint> findEndpoint(String capability) {
        return switch (capability == null ? "" : capability.toUpperCase(Locale.ROOT)) {
            case "AUDIO_STT" -> Optional.of(new ProtocolEndpoint(ProtocolHttpMethod.WEBSOCKET, "/api/v3/sauc/bigmodel_nostream"));
            case "AUDIO_TTS" -> Optional.of(new ProtocolEndpoint(ProtocolHttpMethod.POST, "/api/v3/tts/unidirectional/sse"));
            default -> Optional.empty();
        };
    }
    @Override public Optional<ProtocolEndpoint> modelDiscoveryEndpoint() { return Optional.empty(); }
}
