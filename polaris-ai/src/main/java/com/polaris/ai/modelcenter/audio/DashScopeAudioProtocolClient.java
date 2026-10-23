package com.polaris.ai.modelcenter.audio;

import com.polaris.ai.modelcenter.protocol.ProtocolAdapterRegistry;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.audio.*;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import com.polaris.ai.runtime.usage.UsageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.*;

import static com.polaris.ai.modelcenter.audio.NativeSpeechSupport.*;

/** 百炼 Qwen3 ASR/TTS 与 Qwen-Audio 3.1 TTS；SSE 音频有界合并，忽略签名下载地址。 */
@Component
public class DashScopeAudioProtocolClient implements AudioProtocolClient {
    private final ProtocolAdapterRegistry registry;
    public DashScopeAudioProtocolClient(ProtocolAdapterRegistry registry) { this.registry = registry; }
    @Override public boolean supports(String protocol) { return "DASHSCOPE_AUDIO".equals(protocol); }
    @Override public AudioProtocolResult<AudioSttResult> transcribe(ModelRuntimeSpec runtime, AudioSttInvocation invocation) {
        try {
            if (!invocation.vocabulary().isEmpty() && !RecognitionVocabulary.supported(runtime.protocolCode(),runtime.modelName())) throw failed();
            if (NativeRealtimeAsrClient.supports(runtime.protocolCode(), runtime.modelName())) {
                byte[] wav = input(invocation.audio());
                NormalizedUsage[] measured = { NormalizedUsage.unknown() };
                try (var session = new NativeRealtimeAsrClient().open(runtime, ignored -> { }, usage -> measured[0] = usage)) {
                    for (int offset = 44; offset < wav.length; offset += 6400) session.audio(Arrays.copyOfRange(wav, offset, Math.min(wav.length, offset + 6400)));
                    return new AudioProtocolResult<>(new AudioSttResult(session.finish(), List.of()), measured[0], null);
                }
            }
            if (!runtime.modelName().matches("qwen3-asr-flash(?:-\\d{4}-\\d{2}-\\d{2})?")) throw failed();
            var values = parameters(runtime, invocation.overrides());
            fixed(values, "timestamps", false); fixed(values, "speakerDiarization", false); fixed(values, "punctuation", true);
            Map<String, Object> options = new LinkedHashMap<>(); options.put("enable_itn", false);
            if (values.get("language") != null && !values.get("language").toString().isBlank()) options.put("language", values.get("language"));
            String audio = "data:audio/wav;base64," + Base64.getEncoder().encodeToString(input(invocation.audio()));
            List<Map<String,Object>> messages = new ArrayList<>();
            if (!invocation.vocabulary().isEmpty()) messages.add(Map.of("role","system","content",List.of(Map.of("text","实体词表：" + String.join("、",invocation.vocabulary())))));
            messages.add(Map.of("role", "user", "content", List.of(Map.of("audio", audio))));
            var body = Map.of("model", runtime.modelName(), "input", Map.of("messages", messages),
                    "parameters", Map.of("asr_options", options));
            com.fasterxml.jackson.databind.JsonNode[] result = { null };
            exchange(runtime, request(runtime, "AUDIO_STT", body).build(), stream -> result[0] = json(stream));
            var root = result[0];
            if (root == null || (root.hasNonNull("code") && !root.path("code").asText().isEmpty())) throw failed();
            var choice = root.path("output").path("choices").path(0);
            if (!"stop".equals(choice.path("finish_reason").asText())) throw failed();
            StringBuilder text = new StringBuilder();
            for (var content : choice.path("message").path("content")) if (content.hasNonNull("text")) text.append(content.path("text").asText());
            if (text.length() > 2000) throw failed();
            var usage = root.path("usage");
            Integer in = count(usage.path("input_tokens")), out = count(usage.path("output_tokens"));
            Integer total = in != null && out != null && (long) in + out <= Integer.MAX_VALUE ? in + out : null;
            Long millis = usage.path("seconds").isNumber() ? Math.round(usage.path("seconds").asDouble() * 1000) : null;
            return new AudioProtocolResult<>(new AudioSttResult(text.toString(), List.of()),
                    new NormalizedUsage(in, out, total, null, millis, null, Map.of(), UsageSource.PROVIDER_REPORTED), root.path("request_id").asText(null));
        } catch (com.polaris.common.exception.ServiceException rejected) { throw rejected; }
        catch (Exception invalid) { throw failed(); }
    }
    @Override public AudioProtocolResult<AudioTtsResult> synthesize(ModelRuntimeSpec runtime, AudioTtsInvocation invocation) {
        try {
            text(invocation.text());
            if (!DashScopeSpeechModels.supportsTts(runtime.modelName())) throw failed();
            boolean qwenAudio = DashScopeSpeechModels.qwenAudioTts(runtime.modelName());
            var values = parameters(runtime, invocation.overrides());
            fixed(values, "format", "wav"); fixed(values, "sampleRate", 24000); fixed(values, "speed", 1); fixed(values, "pitch", 0);
            String selectedVoice = voice(values);
            if (qwenAudio && !DashScopeSpeechModels.qwenAudioVoices().contains(selectedVoice)) throw failed();
            // 统一向上返回 WAV；新版内部请求裸 PCM，避免分段 WAV 头重复及直接包装已编码音频。
            var body = Map.of("model", runtime.modelName(), "input", qwenAudio
                    ? Map.of("text", invocation.text(), "voice", selectedVoice, "format", "pcm", "sample_rate", 24000)
                    : Map.of("text", invocation.text(), "voice", selectedVoice, "language_type", "Auto"));
            var bytes = new ByteArrayOutputStream(); boolean[] stop = { false }; Long[] characters = { null }; String[] id = { null };
            Integer[] tokens = { null, null, null };
            exchange(runtime, request(runtime, "AUDIO_TTS", body).header("X-DashScope-SSE", "enable").build(), stream -> events(stream, event -> {
                if (stop[0] || event == null || event.hasNonNull("code") && !event.path("code").asText().isEmpty()) throw failed();
                append(bytes, event.path("output").path("audio").path("data"));
                var usage = event.path("usage");
                if (usage.path("characters").isIntegralNumber() && usage.path("characters").canConvertToLong()
                        && usage.path("characters").longValue() >= 0) characters[0] = usage.path("characters").longValue();
                if (qwenAudio) {
                    String[] fields = { "input_tokens", "output_tokens", "total_tokens" };
                    for (int i = 0; i < fields.length; i++) {
                        Integer measured = count(usage.path(fields[i]));
                        // SSE 用量为累计值，按最新有效值覆盖，不逐事件相加。
                        if (measured != null) tokens[i] = measured;
                    }
                }
                id[0] = event.path("request_id").asText(id[0]);
                if ("stop".equals(event.path("output").path("finish_reason").asText())) stop[0] = true;
            }));
            if (!stop[0]) throw failed();
            if (qwenAudio && tokens[2] == null && tokens[0] != null && tokens[1] != null
                    && (long) tokens[0] + tokens[1] <= Integer.MAX_VALUE) tokens[2] = tokens[0] + tokens[1];
            boolean measured = characters[0] != null || Arrays.stream(tokens).anyMatch(Objects::nonNull);
            return new AudioProtocolResult<>(new AudioTtsResult(wav(bytes.toByteArray(), 24000), "audio/wav", 24000),
                    new NormalizedUsage(tokens[0], tokens[1], tokens[2], characters[0], null, null, Map.of(), measured ? UsageSource.PROVIDER_REPORTED : UsageSource.UNKNOWN), id[0]);
        } catch (com.polaris.common.exception.ServiceException rejected) { throw rejected; }
        catch (Exception invalid) { throw failed(); }
    }
    private HttpRequest.Builder request(ModelRuntimeSpec runtime, String capability, Object body) throws Exception {
        return HttpRequest.newBuilder(endpoint(registry, runtime, capability)).timeout(Duration.ofMillis(runtime.runtimePolicy().readTimeoutMs()))
                .header("Authorization", "Bearer " + key(runtime)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
    }
    private Integer count(com.fasterxml.jackson.databind.JsonNode value) { return value.canConvertToInt() && value.isIntegralNumber() && value.intValue() >= 0 ? value.intValue() : null; }
}
