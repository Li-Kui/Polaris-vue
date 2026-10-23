package com.polaris.ai.customer;

import com.polaris.ai.customer.application.CustomerServiceRealtimeSpeechGateway;
import com.polaris.ai.customer.application.CustomerServiceSpeechGateway;
import com.polaris.ai.modelcenter.audio.NativeRealtimeAsrClient;
import com.polaris.ai.modelcenter.runtime.ModelDefinitionResolver;
import com.polaris.ai.modelcenter.runtime.ModelExecutionLimiter;
import com.polaris.ai.modelcenter.runtime.ModelRuntimeRequest;
import com.polaris.ai.modelcenter.runtime.ModelRuntimeResolver;
import com.polaris.ai.runtime.audio.AudioSttInvocation;
import com.polaris.ai.runtime.audio.ByteArrayAudioInput;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import com.polaris.ai.safety.dto.ModerationRequest;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.service.IModerationFacade;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 实时 ASR 复用模型权限、发布哈希、QPS／并发许可与最终文本内容检查。 */
@Service
public class DefaultCustomerServiceRealtimeSpeechGateway implements CustomerServiceRealtimeSpeechGateway {
    private final CustomerServiceSpeechGateway speech;
    private final ModelDefinitionResolver definitions;
    private final ModelRuntimeResolver runtimes;
    private final NativeRealtimeAsrClient client;
    private final ModelExecutionLimiter limiter;
    private final IModerationFacade moderation;
    public DefaultCustomerServiceRealtimeSpeechGateway(CustomerServiceSpeechGateway speech, ModelDefinitionResolver definitions,
            ModelRuntimeResolver runtimes, NativeRealtimeAsrClient client, ModelExecutionLimiter limiter, IModerationFacade moderation) {
        this.speech=speech; this.definitions=definitions; this.runtimes=runtimes; this.client=client; this.limiter=limiter; this.moderation=moderation;
    }
    @Override public boolean supports(Long id) {
        try {
            speech.validate(id); var model=definitions.resolve(id,"AUDIO_STT");
            if (!NativeRealtimeAsrClient.supports(model.protocolCode(),model.modelName()) || !"DIRECT".equals(model.networkMode())) return false;
            // 豆包双向接口不接受一句话接口的 language 约束，不静默忽略该参数。
            return !"DOUBAO_SPEECH".equals(model.protocolCode()) || !model.invocation().parameters().hasNonNull("language")
                    || model.invocation().parameters().path("language").asText().isBlank();
        } catch (RuntimeException invalid) { return false; }
    }
    @Override public Session open(Long id, String hash, BooleanSupplier active, Consumer<String> partial, Consumer<NormalizedUsage> usage) {
        return open(id,hash,List.of(),active,partial,usage);
    }
    @Override public Session open(Long id, String hash, List<String> vocabulary, BooleanSupplier active, Consumer<String> partial, Consumer<NormalizedUsage> usage) {
        speech.validateVocabulary(id,vocabulary);
        if (!supports(id) || !Objects.equals(hash,speech.validate(id)) || !active.getAsBoolean()) throw unavailable();
        var invocation=new AudioSttInvocation(new ByteArrayAudioInput(new byte[6444],"stream.wav","audio/wav"),Map.of());
        var runtime=runtimes.resolve(new ModelRuntimeRequest<>(id,invocation,Set.of(),List.of()));
        if (!Objects.equals(hash,runtime.runtimeDefinitionHash())) throw unavailable();
        var permit=limiter.acquire(id,"AUDIO_STT",runtime.runtimePolicy());
        Session nativeSession;
        try { nativeSession=vocabulary == null || vocabulary.isEmpty()
                ? client.open(runtime, text -> { if (active.getAsBoolean()) partial.accept(text); },usage)
                : client.open(runtime,vocabulary, text -> { if (active.getAsBoolean()) partial.accept(text); },usage); }
        catch (RuntimeException invalid) { permit.close(); throw unavailable(); }
        return new Session() {
            final AtomicBoolean closed=new AtomicBoolean();
            void check() { if (closed.get() || !active.getAsBoolean()) { close(); throw unavailable(); } }
            @Override public void audio(byte[] pcm) { check(); nativeSession.audio(pcm); }
            @Override public String finish() {
                check();
                try {
                    String text=nativeSession.finish(); check();
                    if (!Objects.equals(hash,speech.validate(id))) throw unavailable();
                    var result=moderation.moderate(ModerationRequest.of(ModerationScene.CHAT_INPUT,text,"CUSTOMER_SERVICE","realtime-speech"));
                    if (result == null || !result.isAllowed()) throw new ServiceException("CONTENT_BLOCKED: 识别文本未通过检测",400);
                    return text.strip();
                } finally { close(); }
            }
            @Override public void close() { if (closed.compareAndSet(false,true)) { try { nativeSession.close(); } finally { permit.close(); } } }
        };
    }
    private ServiceException unavailable() { return new ServiceException("SPEECH_UNAVAILABLE: 实时识别不可用或会话已失效",409); }
}
