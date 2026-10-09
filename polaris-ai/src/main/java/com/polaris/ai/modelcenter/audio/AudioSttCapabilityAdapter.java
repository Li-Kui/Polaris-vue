package com.polaris.ai.modelcenter.audio;

import com.polaris.ai.runtime.CapabilityAdapter;
import com.polaris.ai.runtime.ModelExecutionResult;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.audio.AudioSttInvocation;
import com.polaris.ai.runtime.audio.AudioSttResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AudioSttCapabilityAdapter implements
        CapabilityAdapter<AudioSttInvocation, AudioSttResult> {

    private final List<AudioProtocolClient> clients;

    public AudioSttCapabilityAdapter(List<AudioProtocolClient> clients) {
        this.clients = List.copyOf(clients);
    }

    @Override public String capabilityCode() { return "AUDIO_STT"; }
    @Override public Class<AudioSttInvocation> invocationType() { return AudioSttInvocation.class; }
    @Override public Class<AudioSttResult> resultType() { return AudioSttResult.class; }

    @Override
    public ModelExecutionResult<AudioSttResult> execute(
            ModelRuntimeSpec runtime, AudioSttInvocation invocation) {
        AudioProtocolResult<AudioSttResult> result = clients.stream()
                .filter(item -> item.supports(runtime.protocolCode()))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "AUDIO_PROTOCOL_NOT_SUPPORTED: " + runtime.protocolCode()))
                .transcribe(runtime, invocation);
        return new ModelExecutionResult<>(
                result.value(), result.usage(), result.providerRequestId());
    }
}
