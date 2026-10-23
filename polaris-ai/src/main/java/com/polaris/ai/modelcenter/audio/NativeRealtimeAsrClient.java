package com.polaris.ai.modelcenter.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.polaris.ai.customer.application.CustomerServiceRealtimeSpeechGateway.Session;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.audio.RecognitionVocabulary;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.polaris.ai.modelcenter.audio.NativeSpeechSupport.*;

/** 两家实时 ASR 原生事件适配；PCM 直传，所有缓冲、事件和会话均有界。 */
@Component
public class NativeRealtimeAsrClient {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NativeRealtimeAsrClient.class);
    public static boolean supports(String protocol, String model) {
        return model != null && ("DOUBAO_SPEECH".equals(protocol) && model.matches("volc\\.(seedasr|bigasr)\\.sauc\\.(duration|concurrent)")
                || "DASHSCOPE_AUDIO".equals(protocol) && model.matches("qwen3-asr-flash-realtime(?:-\\d{4}-\\d{2}-\\d{2})?"));
    }
    public Session open(ModelRuntimeSpec runtime, Consumer<String> partial, Consumer<NormalizedUsage> usage) {
        return open(runtime,List.of(),partial,usage);
    }
    public Session open(ModelRuntimeSpec runtime, List<String> vocabulary, Consumer<String> partial, Consumer<NormalizedUsage> usage) {
        if (!supports(runtime.protocolCode(), runtime.modelName()) || !"DIRECT".equals(runtime.networkMode())) throw failed();
        vocabulary = RecognitionVocabulary.normalize(vocabulary);
        if (!vocabulary.isEmpty() && !RecognitionVocabulary.supported(runtime.protocolCode(),runtime.modelName())) throw failed();
        var state = new Stream(runtime, partial, usage, vocabulary);
        try { state.connect(); return state; }
        catch (Exception invalid) {
            state.close(); if (invalid instanceof InterruptedException) Thread.currentThread().interrupt();
            Throwable cause = invalid instanceof ExecutionException && invalid.getCause() != null ? invalid.getCause() : invalid;
            if (cause instanceof WebSocketHandshakeException handshake) throw httpFailure(handshake.getResponse().statusCode());
            if (cause instanceof com.polaris.common.exception.ServiceException controlled) throw controlled;
            throw failed();
        }
    }
    static final class Stream implements Session, WebSocket.Listener {
        final ModelRuntimeSpec runtime;
        final Consumer<String> partial;
        final Consumer<NormalizedUsage> usage;
        final boolean ali;
        final long deadline;
        final CompletableFuture<String> result = new CompletableFuture<>();
        final CompletableFuture<Void> configured = new CompletableFuture<>();
        final ByteArrayOutputStream binary = new ByteArrayOutputStream();
        final StringBuilder json = new StringBuilder();
        final LinkedHashMap<String,String> completed = new LinkedHashMap<>();
        volatile WebSocket socket;
        volatile boolean closing, finishing;
        volatile String phase = "CONNECT";
        int bytes, events, responseBytes;
        byte[] pendingAudio;
        final List<String> vocabulary;
        Stream(ModelRuntimeSpec runtime, Consumer<String> partial, Consumer<NormalizedUsage> usage) {
            this(runtime,partial,usage,List.of());
        }
        Stream(ModelRuntimeSpec runtime, Consumer<String> partial, Consumer<NormalizedUsage> usage,List<String> vocabulary) {
            this.runtime = runtime; this.partial = partial; this.usage = usage;
            this.vocabulary = RecognitionVocabulary.normalize(vocabulary);
            ali = "DASHSCOPE_AUDIO".equals(runtime.protocolCode());
            deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.min(60000, runtime.runtimePolicy().readTimeoutMs()));
        }
        void connect() throws Exception {
            URI base = URI.create(runtime.baseUrl());
            if (!Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null
                    || base.getQuery() != null || base.getFragment() != null || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) throw failed();
            var uri = new URI("https".equals(base.getScheme()) ? "wss" : "ws", null, base.getHost(), base.getPort(),
                    ali ? "/api-ws/v1/realtime" : "/api/v3/sauc/bigmodel_async", ali ? "model=" + runtime.modelName() : null, null);
            var builder = client(runtime).newWebSocketBuilder().connectTimeout(Duration.ofMillis(runtime.runtimePolicy().connectTimeoutMs()));
            if (ali) builder.header("Authorization", "Bearer " + key(runtime));
            else builder.header("X-Api-Key", key(runtime)).header("X-Api-Resource-Id", runtime.modelName()).header("X-Api-Request-Id", UUID.randomUUID().toString());
            socket = builder.buildAsync(uri, this).get(Math.min(remaining(), runtime.runtimePolicy().connectTimeoutMs()), TimeUnit.MILLISECONDS);
            phase = "CONFIGURE";
            if (ali) {
                Map<String,Object> options = new LinkedHashMap<>();
                Object language = runtime.invocationParameters().get("language");
                if (language != null && !language.toString().isBlank()) options.put("language", language);
                send(Map.of("type", "session.update", "event_id", UUID.randomUUID().toString(), "session", Map.of(
                        "input_audio_format", "pcm", "sample_rate", 16000, "input_audio_transcription", options,
                        "turn_detection", Map.of("type", "server_vad", "threshold", .2, "silence_duration_ms", 800))));
                configured.get(Math.min(remaining(), runtime.runtimePolicy().connectTimeoutMs()), TimeUnit.MILLISECONDS);
            } else {
                if (runtime.invocationParameters().get("language") != null && !runtime.invocationParameters().get("language").toString().isBlank()) throw failed();
                Map<String,Object> request = new LinkedHashMap<>(Map.of("model_name", "bigmodel", "enable_nonstream", true, "enable_itn", false,
                        "enable_punc", runtime.invocationParameters().getOrDefault("punctuation", true), "result_type", "full"));
                recognitionCorpus(request,vocabulary);
                var body = Map.of("user", Map.of("uid", UUID.randomUUID().toString()),
                        "audio", Map.of("format", "pcm", "codec", "raw", "rate", 16000, "bits", 16, "channel", 1),
                        "request", request);
                socket.sendBinary(ByteBuffer.wrap(DoubaoSpeechProtocolClient.frame(0x10, 0x11, JSON.writeValueAsBytes(body))), true).get(remaining(), TimeUnit.MILLISECONDS);
            }
            phase = "AUDIO";
        }
        @Override public synchronized void audio(byte[] pcm) {
            if (closing || finishing || pcm == null || pcm.length == 0 || pcm.length > 6400 || (pcm.length & 1) != 0 || bytes + pcm.length > 640000 || result.isDone()) throw failed();
            try {
                bytes += pcm.length;
                if (ali) send(Map.of("type", "input_audio_buffer.append", "event_id", UUID.randomUUID().toString(), "audio", Base64.getEncoder().encodeToString(pcm)));
                else {
                    if (pendingAudio != null) socket.sendBinary(ByteBuffer.wrap(DoubaoSpeechProtocolClient.frame(0x20, 0x01, pendingAudio)), true).get(remaining(), TimeUnit.MILLISECONDS);
                    pendingAudio = pcm.clone();
                }
            } catch (Exception invalid) { close(); if (invalid instanceof InterruptedException) Thread.currentThread().interrupt(); throw failed(); }
        }
        @Override public String finish() {
            synchronized (this) { if (closing || finishing || bytes < 6400) throw failed(); finishing = true; phase = "FINISH"; }
            try {
                if (ali) send(Map.of("type", "session.finish", "event_id", UUID.randomUUID().toString()));
                else socket.sendBinary(ByteBuffer.wrap(DoubaoSpeechProtocolClient.frame(0x22, 0x01, pendingAudio)), true).get(remaining(), TimeUnit.MILLISECONDS);
                String text = result.get(remaining(), TimeUnit.MILLISECONDS);
                if (text == null || text.isBlank()) throw new com.polaris.common.exception.ServiceException("SPEECH_TEXT_INVALID: 未识别到有效语句",409);
                if (text.length() > 2000) throw failed();
                return text;
            } catch (Exception invalid) {
                if (invalid instanceof InterruptedException) Thread.currentThread().interrupt();
                Throwable cause = invalid instanceof ExecutionException && invalid.getCause() != null ? invalid.getCause() : invalid;
                if (cause instanceof com.polaris.common.exception.ServiceException controlled) throw controlled;
                throw failed();
            }
            finally { close(); }
        }
        void send(Map<String,?> value) throws Exception { socket.sendText(JSON.writeValueAsString(value), true).get(remaining(), TimeUnit.MILLISECONDS); }
        long remaining() { long value = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()); if (value <= 0) throw failed(); return value; }
        @Override public void close() {
            closing = true; pendingAudio = null;
            try { if (socket != null) socket.abort(); }
            finally { result.completeExceptionally(failed()); configured.completeExceptionally(failed()); }
        }
        void reject(Exception error) {
            String code = error instanceof com.polaris.common.exception.ServiceException && error.getMessage() != null ? error.getMessage().split(":", 2)[0] : "UNCLASSIFIED";
            if (!code.matches("NATIVE_SPEECH_[A-Za-z0-9_.]+")) code = "UNCLASSIFIED";
            log.warn("实时识别未完成: provider={}, phase={}, failure={}, code={}", ali ? "ALI" : "DOUBAO", phase, error.getClass().getSimpleName(), code);
            var controlled = error instanceof com.polaris.common.exception.ServiceException safe ? safe : failed();
            result.completeExceptionally(controlled); configured.completeExceptionally(controlled); close();
        }
        void preview(String value) { if (value.length() > 2000) throw failed(); if (!closing && !value.isBlank()) partial.accept(value); }
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
            try {
                if (ali || (responseBytes += data.remaining()) > 4*1024*1024 || binary.size() + data.remaining() > 1024*1024 || ++events > 4000) throw failed();
                byte[] bytes = new byte[data.remaining()]; data.get(bytes); binary.writeBytes(bytes);
                if (last) {
                    var frame = DoubaoSpeechProtocolClient.decode(binary.toByteArray()); binary.reset();
                    JsonNode payload = frame.payload(), value = payload.path("result"); if (value.isArray()) value = value.path(0);
                    String text = value.path("text").asText("");
                    if (frame.last()) {
                        if (!finishing) throw failed();
                        Long millis = payload.path("audio_info").path("duration").isIntegralNumber() ? payload.path("audio_info").path("duration").longValue() : null;
                        usage.accept(new NormalizedUsage(null,null,null,null,millis,null,Map.of(),millis == null ? null : com.polaris.ai.runtime.usage.UsageSource.PROVIDER_REPORTED));
                        result.complete(text);
                    } else preview(text);
                }
            } catch (Exception invalid) { reject(invalid); }
            socket.request(1); return null;
        }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            try {
                if (!ali || (responseBytes += data.length()*2) > 4*1024*1024 || json.length() + data.length() > 65536 || ++events > 4000) throw failed();
                json.append(data);
                if (last) { JsonNode event = JSON.readTree(json.toString()); json.setLength(0); acceptAli(event); }
            } catch (Exception invalid) { reject(invalid); }
            socket.request(1); return null;
        }
        void acceptAli(JsonNode event) {
            String type = event.path("type").asText("");
            if ("error".equals(type) || type.endsWith(".failed")) {
                String code = event.path("error").path("code").asText("");
                if (Set.of("invalid_request_error", "invalid_value", "invalid_api_key", "insufficient_quota", "rate_limit_exceeded", "unsupported_model", "InvalidParameter", "ModelNotFound", "InvalidApiKey", "AllocationQuota.FreeTierOnly").contains(code)) {
                    throw new com.polaris.common.exception.ServiceException("NATIVE_SPEECH_CODE_" + code + ": 实时识别业务请求未成功");
                }
                throw failed();
            }
            if ("session.updated".equals(type)) configured.complete(null);
            else if ("conversation.item.input_audio_transcription.text".equals(type)) {
                preview(String.join("", completed.values()) + event.path("text").asText("") + event.path("stash").asText(""));
            } else if ("conversation.item.input_audio_transcription.completed".equals(type)) {
                if (completed.size() >= 100 || !event.path("item_id").isTextual()) throw failed();
                completed.put(event.path("item_id").asText(), event.path("transcript").asText("")); preview(String.join("", completed.values()));
            } else if ("session.finished".equals(type)) {
                if (!finishing) throw failed(); usage.accept(NormalizedUsage.unknown()); result.complete(String.join("", completed.values()));
            }
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
            if (!result.isDone()) { log.warn("实时识别提前关闭: provider={}, phase={}, status={}", ali ? "ALI" : "DOUBAO", phase, status); close(); }
            return null;
        }
        @Override public void onError(WebSocket socket, Throwable error) {
            if (!closing) {
                StringBuilder flags = new StringBuilder(); Throwable value = error;
                for (int depth=0; value!=null && depth<3; depth++, value=value.getCause()) {
                    String message = Objects.toString(value.getMessage(), "").toLowerCase(Locale.ROOT);
                    for (String keyword : List.of("rsv", "compress", "mask", "opcode", "continuation", "utf", "reserved", "close", "payload", "status")) if (message.contains(keyword)) flags.append(keyword).append(',');
                }
                log.warn("实时识别连接异常: provider={}, phase={}, failure={}, protocolFlags={}", ali ? "ALI" : "DOUBAO", phase, error.getClass().getSimpleName(), flags);
            }
            close();
        }
    }
}
