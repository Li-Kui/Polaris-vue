package com.polaris.ai.customer;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.customer.application.CustomerServiceSpeechGateway;
import com.polaris.ai.modelcenter.runtime.ModelDefinitionResolver;
import com.polaris.ai.modelcenter.runtime.ModelRuntimeRequest;
import com.polaris.ai.modelcenter.runtime.ModelRuntimeResolver;
import com.polaris.ai.runtime.CapabilityExecutor;
import com.polaris.ai.runtime.audio.*;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import com.polaris.ai.safety.dto.ModerationRequest;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.service.IModerationFacade;
import com.polaris.ai.service.IAiModelConfigService;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Service
public class DefaultCustomerServiceSpeechGateway implements CustomerServiceSpeechGateway {
    private final ModelDefinitionResolver models;
    private final ModelRuntimeResolver runtimes;
    private final CapabilityExecutor executor;
    private final IAiModelConfigService catalog;
    private final IModerationFacade moderation;
    public DefaultCustomerServiceSpeechGateway(ModelDefinitionResolver models, ModelRuntimeResolver runtimes,
            CapabilityExecutor executor, IAiModelConfigService catalog, IModerationFacade moderation) {
        this.models = models; this.runtimes = runtimes; this.executor = executor; this.catalog = catalog; this.moderation = moderation;
    }
    @Override
    public List<Model> models() {
        requireActor();
        List<Model> result = new ArrayList<>();
        for (String capability : List.of("AUDIO_STT", "AUDIO_TTS")) {
            catalog.selectAvailableModelConfigsByCapability(capability, null, true).stream().limit(200).forEach(model -> {
                try { validate(model.getId(), capability); result.add(new Model(model.getId(), model.getName(), capability)); }
                catch (RuntimeException unavailable) { /* 不暴露不可用模型。 */ }
            });
        }
        return List.copyOf(result);
    }
    @Override public String validate(Long modelId) { return validate(modelId, "AUDIO_STT"); }
    @Override public void validateVocabulary(Long modelId, List<String> vocabulary) {
        if (RecognitionVocabulary.normalize(vocabulary).isEmpty()) return;
        validate(modelId);
        var model = models.resolve(modelId,"AUDIO_STT");
        if (!RecognitionVocabulary.supported(model.protocolCode(),model.modelName())) throw new ServiceException("SPEECH_VOCABULARY_UNSUPPORTED: 当前模型不支持术语提示，请选择豆包识别或阿里云普通HTTP识别模型",400);
    }
    @Override public String validateTts(Long modelId) { return validate(modelId, "AUDIO_TTS"); }
    private String validate(Long modelId, String capability) {
        try {
            var caller = CallerContextHolder.require();
            if (caller instanceof com.polaris.ai.core.context.CustomerServiceVisitorContext visitor) {
                Long granted = "AUDIO_STT".equals(capability) ? visitor.sttModelId() : visitor.ttsModelId();
                if (visitor.speechLimit() == 0 || modelId == null || !Objects.equals(modelId, granted)) throw unavailable();
            } else requireActor();
            var model = models.resolve(modelId, capability);
            if (!Set.of("OPENAI_COMPATIBLE", "DASHSCOPE_AUDIO", "DOUBAO_SPEECH").contains(model.protocolCode())) throw unavailable();
            if ("DASHSCOPE_AUDIO".equals(model.protocolCode())) {
                if ("AUDIO_TTS".equals(capability)) {
                    if (!com.polaris.ai.modelcenter.audio.DashScopeSpeechModels.supportsTts(model.modelName())) throw unavailable();
                } else if (model.modelName() == null || !model.modelName().matches("qwen3-asr-flash(?:-realtime)?(?:-\\d{4}-\\d{2}-\\d{2})?")) throw unavailable();
            } else if ("DOUBAO_SPEECH".equals(model.protocolCode())) {
                if (model.modelName() == null || !("AUDIO_STT".equals(capability)
                        ? model.modelName().matches("volc\\.(seedasr|bigasr)\\.sauc\\.(duration|concurrent)")
                        : Set.of("seed-tts-2.0", "seed-tts-1.0").contains(model.modelName()))) throw unavailable();
            }
            return model.runtimeDefinitionHash();
        } catch (RuntimeException invalid) { throw unavailable(); }
    }
    @Override
    public String transcribe(Long modelId, String hash, byte[] wav, BooleanSupplier active, Consumer<NormalizedUsage> usage) {
        return transcribe(modelId,hash,wav,List.of(),active,usage);
    }
    @Override
    public String transcribe(Long modelId, String hash, byte[] wav, List<String> vocabulary, BooleanSupplier active, Consumer<NormalizedUsage> usage) {
        if (!Objects.equals(hash, validate(modelId))) throw unavailable();
        validateVocabulary(modelId,vocabulary);
        check(active);
        var invocation = new AudioSttInvocation(new ByteArrayAudioInput(wav, "speech.wav", "audio/wav"), Map.of(),vocabulary);
        var runtime = runtimes.resolve(new ModelRuntimeRequest<>(modelId, invocation, Set.of(), List.of()));
        if (!Objects.equals(hash, runtime.runtimeDefinitionHash())) throw unavailable();
        com.polaris.ai.runtime.ModelExecutionResult<AudioSttResult> response;
        try { response = executor.execute(runtime, invocation, AudioSttResult.class); }
        catch (RuntimeException failed) { throw new ServiceException("SPEECH_PROVIDER_FAILED: 语音识别服务暂不可用", 502); }
        if (response == null) throw unavailable();
        usage.accept(response.usage());
        check(active);
        if (!Objects.equals(hash, validate(modelId))) throw unavailable();
        String text = response.value() == null ? null : response.value().text();
        if (text == null || text.isBlank() || text.length() > 2000) throw new ServiceException("SPEECH_TEXT_INVALID: 未识别到有效短文本，请重录或使用文字", 409);
        var result = moderation.moderate(ModerationRequest.of(ModerationScene.CHAT_INPUT, text, "CUSTOMER_SERVICE", "speech"));
        if (result == null || !result.isAllowed()) throw new ServiceException("CONTENT_BLOCKED: 识别内容未通过检测", 400);
        return text.strip();
    }
    @Override
    public AudioTtsResult synthesize(Long modelId, String hash, String text, BooleanSupplier active, Consumer<NormalizedUsage> usage) {
        if (text == null || text.isBlank() || text.length() > 600 || !Objects.equals(hash, validateTts(modelId))) throw unavailable();
        check(active);
        var allowed = moderation.moderate(ModerationRequest.of(ModerationScene.AI_OUTPUT, text, "CUSTOMER_SERVICE", "speech-playback"));
        if (allowed == null || !allowed.isAllowed()) throw new ServiceException("CONTENT_BLOCKED: 播报内容未通过检测", 400);
        var invocation = new AudioTtsInvocation(text, false, Map.of());
        var runtime = runtimes.resolve(new ModelRuntimeRequest<>(modelId, invocation, Set.of(), List.of()));
        if (!Objects.equals(hash, runtime.runtimeDefinitionHash())) throw unavailable();
        com.polaris.ai.runtime.ModelExecutionResult<AudioTtsResult> response;
        try { response = executor.execute(runtime, invocation, AudioTtsResult.class); }
        catch (RuntimeException failed) { throw new ServiceException("SPEECH_PROVIDER_FAILED: 语音合成服务暂不可用", 502); }
        if (response == null) throw unavailable();
        usage.accept(response.usage()); check(active);
        if (!Objects.equals(hash, validateTts(modelId))) throw unavailable();
        var audio = response.value();
        if (audio == null || audio.audio().length == 0 || audio.audio().length > 8 * 1024 * 1024
                || !Set.of("audio/wav", "audio/mpeg", "audio/mp3").contains(audio.contentType())) throw unavailable();
        return audio;
    }
    private void requireActor() {
        var actor = CallerContextHolder.require();
        if (!actor.isTenantAdmin() || actor.getUserId() == null || actor.getTenantId() == null) throw unavailable();
    }
    private void check(BooleanSupplier active) { if (!active.getAsBoolean()) throw new ServiceException("SPEECH_INTERRUPTED: 会话或本次识别已失效", 409); }
    private ServiceException unavailable() { return new ServiceException("SPEECH_UNAVAILABLE: 识别模型未配置、已变化、协议不支持或无权使用，请检查并重新发布客服", 409); }
}
