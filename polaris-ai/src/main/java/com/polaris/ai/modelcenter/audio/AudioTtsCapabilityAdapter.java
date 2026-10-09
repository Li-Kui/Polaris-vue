package com.polaris.ai.modelcenter.audio;

import com.polaris.ai.runtime.CapabilityAdapter;
import com.polaris.ai.runtime.ModelExecutionResult;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.audio.AudioTtsInvocation;
import com.polaris.ai.runtime.audio.AudioTtsResult;
import com.polaris.ai.runtime.stream.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AudioTtsCapabilityAdapter implements
        CapabilityAdapter<AudioTtsInvocation, AudioTtsResult> {

    private static final int CHUNK_SIZE = 16 * 1024;
    private final List<AudioProtocolClient> clients;

    public AudioTtsCapabilityAdapter(List<AudioProtocolClient> clients) {
        this.clients = List.copyOf(clients);
    }

    @Override public String capabilityCode() { return "AUDIO_TTS"; }
    @Override public Class<AudioTtsInvocation> invocationType() { return AudioTtsInvocation.class; }
    @Override public Class<AudioTtsResult> resultType() { return AudioTtsResult.class; }

    @Override
    public ModelExecutionResult<AudioTtsResult> execute(
            ModelRuntimeSpec runtime, AudioTtsInvocation invocation) {
        AudioProtocolResult<AudioTtsResult> result = client(runtime)
                .synthesize(runtime, invocation);
        return new ModelExecutionResult<>(
                result.value(), result.usage(), result.providerRequestId());
    }

    @Override
    public Flow.Publisher<ModelStreamEvent> stream(
            ModelRuntimeSpec runtime, AudioTtsInvocation invocation) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            private final AtomicBoolean completed = new AtomicBoolean();

            @Override
            public void request(long count) {
                if (count <= 0 || !completed.compareAndSet(false, true)) return;
                try {
                    AudioProtocolResult<AudioTtsResult> result = client(runtime)
                            .synthesize(runtime, invocation);
                    AudioTtsResult audio = result.value();
                    subscriber.onNext(new AudioMetadata(
                            audio.contentType(), audio.sampleRate(), null));
                    byte[] bytes = audio.audio();
                    for (int offset = 0; offset < bytes.length; offset += CHUNK_SIZE) {
                        int length = Math.min(CHUNK_SIZE, bytes.length - offset);
                        subscriber.onNext(new AudioChunk(
                                java.util.Arrays.copyOfRange(
                                        bytes, offset, offset + length)));
                    }
                    subscriber.onNext(new UsageEvent(result.usage()));
                    subscriber.onNext(new CompletedEvent(
                            "stop", result.providerRequestId()));
                    subscriber.onComplete();
                } catch (Throwable error) {
                    subscriber.onError(error);
                }
            }

            @Override public void cancel() { completed.set(true); }
        });
    }

    private AudioProtocolClient client(ModelRuntimeSpec runtime) {
        return clients.stream().filter(item -> item.supports(runtime.protocolCode()))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "AUDIO_PROTOCOL_NOT_SUPPORTED: " + runtime.protocolCode()));
    }
}
