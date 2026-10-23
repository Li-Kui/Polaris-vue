package com.polaris.ai.modelcenter.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.polaris.ai.modelcenter.protocol.ProtocolAdapterRegistry;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.audio.AudioSttInvocation;
import com.polaris.ai.runtime.audio.AudioSttResult;
import com.polaris.ai.runtime.audio.AudioTtsInvocation;
import com.polaris.ai.runtime.audio.AudioTtsResult;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import com.polaris.ai.runtime.usage.UsageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.polaris.ai.modelcenter.audio.NativeSpeechSupport.*;

/** 豆包语音新控制台 API Key；识别走一句话 WebSocket，合成走 V3 SSE。 */
@Component
public class DoubaoSpeechProtocolClient implements AudioProtocolClient {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DoubaoSpeechProtocolClient.class);
    private final ProtocolAdapterRegistry registry;
    public DoubaoSpeechProtocolClient(ProtocolAdapterRegistry registry) { this.registry = registry; }
    @Override public boolean supports(String protocol) { return "DOUBAO_SPEECH".equals(protocol); }
    @Override public AudioProtocolResult<AudioSttResult> transcribe(ModelRuntimeSpec runtime, AudioSttInvocation invocation) {
        WebSocket socket = null;
        long started = System.nanoTime();
        try {
            if (!runtime.modelName().matches("volc\\.(seedasr|bigasr)\\.sauc\\.(duration|concurrent)")) throw failed();
            byte[] wav = input(invocation.audio());
            var values = parameters(runtime, invocation.overrides());
            fixed(values, "timestamps", false); fixed(values, "speakerDiarization", false);
            Map<String, Object> audio = new LinkedHashMap<>(Map.of("format", "pcm", "codec", "raw", "rate", 16000, "bits", 16, "channel", 1));
            if (values.get("language") != null && !values.get("language").toString().isBlank()) audio.put("language", values.get("language"));
            Map<String,Object> requestOptions = new LinkedHashMap<>(Map.of("model_name", "bigmodel", "enable_itn", false, "enable_punc", values.getOrDefault("punctuation", true)));
            recognitionCorpus(requestOptions,invocation.vocabulary());
            var body = Map.of("user", Map.of("uid", UUID.randomUUID().toString()), "audio", audio, "request", requestOptions);
            var uri = endpoint(registry, runtime, "AUDIO_STT");
            URI websocket = new URI("https".equals(uri.getScheme()) ? "wss" : "ws", null, uri.getHost(), uri.getPort(), uri.getPath(), null, null);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(runtime.runtimePolicy().readTimeoutMs());
            var listener = new RecognitionListener();
            socket = client(runtime).newWebSocketBuilder().connectTimeout(Duration.ofMillis(runtime.runtimePolicy().connectTimeoutMs()))
                    .header("X-Api-Key", key(runtime)).header("X-Api-Resource-Id", runtime.modelName())
                    .header("X-Api-Request-Id", UUID.randomUUID().toString()).buildAsync(websocket, listener)
                    .get(remaining(deadline), TimeUnit.MILLISECONDS);
            long connected = System.nanoTime();
            socket.sendBinary(ByteBuffer.wrap(frame(0x10, 0x11, JSON.writeValueAsBytes(body))), true).get(remaining(deadline), TimeUnit.MILLISECONDS);
            // 此处已拿到完整短录音，按 200ms 音频长度分包，但不再按录音时钟重放。
            // 顺序等待发送完成提供背压；原始 WAV 头不进入音频流。
            for (int offset = 44; offset < wav.length; offset += 6400) {
                int end = Math.min(wav.length, offset + 6400); boolean last = end == wav.length;
                if (listener.result.isCompletedExceptionally()) listener.result.get();
                socket.sendBinary(ByteBuffer.wrap(frame(last ? 0x22 : 0x20, 0x01, Arrays.copyOfRange(wav, offset, end))), true)
                        .get(remaining(deadline), TimeUnit.MILLISECONDS);
            }
            long sent = System.nanoTime();
            JsonNode response = listener.result.get(remaining(deadline), TimeUnit.MILLISECONDS);
            long completed = System.nanoTime();
            var result = response.path("result");
            if (result.isArray()) result = result.path(0);
            String text = result.path("text").asText("");
            if (text.length() > 2000) throw failed();
            Long millis = response.path("audio_info").path("duration").isIntegralNumber() ? response.path("audio_info").path("duration").longValue() : null;
            // 仅记录耗时，不记录识别文本、音频、请求体、凭据或供应商原始响应。
            log.info("豆包短录音识别完成: audioMs={}, connectMs={}, sendMs={}, resultWaitMs={}, totalMs={}",
                    (wav.length - 44) / 32, elapsed(started, connected), elapsed(connected, sent),
                    elapsed(sent, completed), elapsed(started, completed));
            return new AudioProtocolResult<>(new AudioSttResult(text, List.of()),
                    new NormalizedUsage(null, null, null, null, millis, null, Map.of(), millis == null ? UsageSource.UNKNOWN : UsageSource.PROVIDER_REPORTED), null);
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw failed(); }
        catch (ExecutionException rejected) {
            if (rejected.getCause() instanceof WebSocketHandshakeException handshake) throw httpFailure(handshake.getResponse().statusCode());
            throw failed();
        }
        catch (Exception invalid) { throw failed(); }
        finally { if (socket != null) socket.abort(); }
    }
    @Override public AudioProtocolResult<AudioTtsResult> synthesize(ModelRuntimeSpec runtime, AudioTtsInvocation invocation) {
        try {
            text(invocation.text());
            if (!Set.of("seed-tts-2.0", "seed-tts-1.0").contains(runtime.modelName())) throw failed();
            var values = parameters(runtime, invocation.overrides()); fixed(values, "format", "mp3"); fixed(values, "pitch", 0);
            int sampleRate = ((Number) values.getOrDefault("sampleRate", 24000)).intValue();
            if (!Set.of(8000, 16000, 22050, 24000, 44100, 48000).contains(sampleRate)) throw failed();
            double speed = ((Number) values.getOrDefault("speed", 1)).doubleValue();
            if (!Double.isFinite(speed) || speed < 0.5 || speed > 2) throw failed();
            var body = Map.of("user", Map.of("uid", UUID.randomUUID().toString()), "req_params", Map.of("text", invocation.text(), "speaker", voice(values),
                    "audio_params", Map.of("format", "mp3", "sample_rate", sampleRate, "speech_rate", Math.round((speed - 1) * 100))));
            var request = HttpRequest.newBuilder(endpoint(registry, runtime, "AUDIO_TTS")).timeout(Duration.ofMillis(runtime.runtimePolicy().readTimeoutMs()))
                    .header("X-Api-Key", key(runtime)).header("X-Api-Resource-Id", runtime.modelName()).header("X-Api-Request-Id", UUID.randomUUID().toString())
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
            var bytes = new ByteArrayOutputStream(); boolean[] stop = { false }; Long[] words = { null };
            exchange(runtime, request, stream -> events(stream, event -> {
                if (event == null || stop[0] || !event.path("code").isIntegralNumber()) throw failed();
                int code = event.path("code").intValue();
                if (code != 0 && code != 20000000) throw businessFailure(code);
                append(bytes, event.path("data"));
                if (code == 20000000) {
                    stop[0] = true;
                    if (event.path("usage").path("text_words").isIntegralNumber()) words[0] = event.path("usage").path("text_words").longValue();
                }
            }));
            if (!stop[0] || bytes.size() == 0) throw failed();
            // text_words 单位为词，保留原单位，不冒充字符或 Token。
            return new AudioProtocolResult<>(new AudioTtsResult(bytes.toByteArray(), "audio/mpeg", sampleRate),
                    new NormalizedUsage(null, null, null, null, null, null, words[0] == null ? Map.of() : Map.of("providerTextWords", words[0]),
                            words[0] == null ? UsageSource.UNKNOWN : UsageSource.PROVIDER_REPORTED), null);
        } catch (com.polaris.common.exception.ServiceException rejected) { throw rejected; }
        catch (Exception invalid) { throw failed(); }
    }
    static byte[] frame(int typeFlags, int serializationCompression, byte[] payload) throws IOException {
        var gzip = new ByteArrayOutputStream(); try (var zipped = new GZIPOutputStream(gzip)) { zipped.write(payload); }
        byte[] compressed = gzip.toByteArray();
        return ByteBuffer.allocate(8 + compressed.length).put((byte) 0x11).put((byte) typeFlags).put((byte) serializationCompression).put((byte) 0)
                .putInt(compressed.length).put(compressed).array();
    }
    static RecognitionFrame decode(byte[] frame) throws IOException {
        if (frame.length < 8 || frame.length > 1024 * 1024 || (frame[0] & 0xff) != 0x11 || frame[3] != 0) throw failed();
        int type = (frame[1] & 0xf0) >>> 4, flags = frame[1] & 0xf;
        if (type == 15) throw businessFailure(ByteBuffer.wrap(frame).getInt(4));
        if (type != 9 || flags > 3 || (frame[2] & 0xf0) != 0x10 || frame.length < ((flags & 1) != 0 ? 12 : 8)) throw failed();
        var buffer = ByteBuffer.wrap(frame); buffer.position(4); if ((flags & 1) != 0) buffer.getInt(); int size = buffer.getInt();
        if (size != buffer.remaining()) throw failed();
        byte[] payload = new byte[size]; buffer.get(payload);
        int compression = frame[2] & 0xf;
        if (compression == 1) try (var unzip = new GZIPInputStream(new ByteArrayInputStream(payload))) {
            payload = unzip.readNBytes(1024 * 1024 + 1); if (payload.length > 1024 * 1024) throw failed();
        } else if (compression != 0) throw failed();
        JsonNode json = JSON.readTree(payload);
        if (json == null || !json.isObject()) throw failed();
        return new RecognitionFrame((flags & 2) != 0, json);
    }
    private static long remaining(long deadline) { long millis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()); if (millis <= 0) throw failed(); return millis; }
    private static long elapsed(long from, long to) { return TimeUnit.NANOSECONDS.toMillis(to - from); }
    record RecognitionFrame(boolean last, JsonNode payload) { }
    static final class RecognitionListener implements WebSocket.Listener {
        final CompletableFuture<JsonNode> result = new CompletableFuture<>();
        private final ByteArrayOutputStream fragments = new ByteArrayOutputStream();
        private int total;
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
            try {
                if ((total += data.remaining()) > 4 * 1024 * 1024 || fragments.size() + data.remaining() > 1024 * 1024) throw failed();
                byte[] bytes = new byte[data.remaining()]; data.get(bytes); fragments.writeBytes(bytes);
                if (last) {
                    var decoded = decode(fragments.toByteArray()); fragments.reset();
                    if (decoded.last()) result.complete(decoded.payload());
                }
            } catch (Exception invalid) { result.completeExceptionally(failed()); socket.abort(); }
            socket.request(1); return null;
        }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) { result.completeExceptionally(failed()); socket.abort(); return null; }
        @Override public CompletionStage<?> onClose(WebSocket socket, int status, String reason) { if (!result.isDone()) result.completeExceptionally(failed()); return null; }
        @Override public void onError(WebSocket socket, Throwable error) { result.completeExceptionally(failed()); }
    }
}
