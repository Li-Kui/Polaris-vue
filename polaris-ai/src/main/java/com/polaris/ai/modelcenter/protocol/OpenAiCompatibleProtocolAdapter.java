package com.polaris.ai.modelcenter.protocol;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** OpenAI 协议族的固定路径映射；实际支持能力仍需 Profile/Discovery/人工声明。 */
@Component
public class OpenAiCompatibleProtocolAdapter implements ProtocolAdapter {

    public static final String PROTOCOL_CODE = "OPENAI_COMPATIBLE";
    public static final int ADAPTER_VERSION = 1;

    private static final Map<String, ProtocolEndpoint> CAPABILITY_ENDPOINTS = Map.of(
            "CHAT_COMPLETION", new ProtocolEndpoint(
                    ProtocolHttpMethod.POST, "/chat/completions"),
            "TEXT_EMBEDDING", new ProtocolEndpoint(
                    ProtocolHttpMethod.POST, "/embeddings"),
            "IMAGE_GENERATION", new ProtocolEndpoint(
                    ProtocolHttpMethod.POST, "/images/generations"),
            "IMAGE_EDIT", new ProtocolEndpoint(
                    ProtocolHttpMethod.POST, "/images/edits"),
            "IMAGE_INPAINT", new ProtocolEndpoint(
                    ProtocolHttpMethod.POST, "/images/edits"),
            "IMAGE_VARIATION", new ProtocolEndpoint(
                    ProtocolHttpMethod.POST, "/images/variations"),
            "AUDIO_TTS", new ProtocolEndpoint(
                    ProtocolHttpMethod.POST, "/audio/speech"),
            "AUDIO_STT", new ProtocolEndpoint(
                    ProtocolHttpMethod.POST, "/audio/transcriptions"));
    private static final ProtocolEndpoint MODEL_DISCOVERY_ENDPOINT =
            new ProtocolEndpoint(ProtocolHttpMethod.GET, "/models");

    @Override
    public String protocolCode() {
        return PROTOCOL_CODE;
    }

    @Override
    public int adapterVersion() {
        return ADAPTER_VERSION;
    }

    @Override
    public Set<String> mappedCapabilities() {
        return CAPABILITY_ENDPOINTS.keySet();
    }

    @Override
    public Optional<ProtocolEndpoint> findEndpoint(String capabilityCode) {
        if (capabilityCode == null || capabilityCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(CAPABILITY_ENDPOINTS.get(
                capabilityCode.trim().toUpperCase(Locale.ROOT)));
    }

    @Override
    public Optional<ProtocolEndpoint> modelDiscoveryEndpoint() {
        return Optional.of(MODEL_DISCOVERY_ENDPOINT);
    }
}
