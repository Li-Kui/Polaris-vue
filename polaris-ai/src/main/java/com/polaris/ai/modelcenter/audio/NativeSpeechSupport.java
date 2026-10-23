package com.polaris.ai.modelcenter.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.modelcenter.protocol.ProtocolAdapterRegistry;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.audio.AudioInput;
import com.polaris.common.exception.ServiceException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** 原生语音有界输入输出；不下载供应商返回的 URL，不跟随重定向。 */
final class NativeSpeechSupport {
    static final ObjectMapper JSON = new ObjectMapper();
    static final int MAX_AUDIO = 8 * 1024 * 1024;
    private static final Map<Integer,HttpClient> CLIENTS = new LinkedHashMap<>();
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "native-speech-deadline"); thread.setDaemon(true); return thread;
    });
    static URI endpoint(ProtocolAdapterRegistry registry, ModelRuntimeSpec runtime, String capability) {
        var path = registry.getRequired(runtime.protocolCode()).findEndpoint(capability, runtime.modelName()).orElseThrow(NativeSpeechSupport::failed);
        URI base = URI.create(runtime.baseUrl());
        if (!("https".equals(base.getScheme()) || "http".equals(base.getScheme())) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) throw failed();
        return base.resolve(path.relativePath());
    }
    static synchronized HttpClient client(ModelRuntimeSpec runtime) {
        int timeout = runtime.runtimePolicy().connectTimeoutMs();
        var cached = CLIENTS.get(timeout);
        if (cached != null) return cached;
        // 只复用无凭据的传输；每个请求仍单独设置授权和截止时间。缓存有界，避免每轮重新建立连接。
        var created = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeout)).followRedirects(HttpClient.Redirect.NEVER).build();
        if (CLIENTS.size() < 8) CLIENTS.put(timeout,created);
        return created;
    }
    static String key(ModelRuntimeSpec runtime) {
        Object key = runtime.credentials().get("apiKey");
        if (key == null || key.toString().isBlank()) throw failed();
        return key.toString();
    }
    static Map<String, Object> parameters(ModelRuntimeSpec runtime, Map<String, Object> overrides) {
        Map<String, Object> values = new LinkedHashMap<>(runtime.invocationParameters()); values.putAll(overrides); return values;
    }
    static void recognitionCorpus(Map<String,Object> request, List<String> vocabulary) throws IOException {
        var words = com.polaris.ai.runtime.audio.RecognitionVocabulary.normalize(vocabulary);
        if (!words.isEmpty()) request.put("corpus",Map.of("context",JSON.writeValueAsString(Map.of("hotwords",words.stream().map(word -> Map.of("word",word)).toList()))));
    }
    static byte[] input(AudioInput audio) throws IOException {
        try (InputStream input = audio.openStream()) {
            byte[] bytes = input.readNBytes(640045);
            if (bytes.length < 44 || bytes.length > 640044 || !"audio/wav".equals(audio.contentType())) throw failed();
            var wav = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (!"RIFF".equals(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII))
                    || !"WAVEfmt ".equals(new String(bytes, 8, 8, java.nio.charset.StandardCharsets.US_ASCII))
                    || wav.getInt(4) != bytes.length - 8 || wav.getInt(16) != 16 || wav.getShort(20) != 1 || wav.getShort(22) != 1
                    || wav.getInt(24) != 16000 || wav.getInt(28) != 32000 || wav.getShort(32) != 2 || wav.getShort(34) != 16
                    || !"data".equals(new String(bytes, 36, 4, java.nio.charset.StandardCharsets.US_ASCII))
                    || wav.getInt(40) != bytes.length - 44 || (bytes.length - 44) % 2 != 0) throw failed();
            return bytes;
        }
    }
    static String voice(Map<String, Object> values) {
        String value = Objects.toString(values.get("voice"), "");
        if (!value.matches("[A-Za-z0-9_.-]{1,100}")) throw failed(); return value;
    }
    static void text(String text) { if (text == null || text.isBlank() || text.length() > 600) throw failed(); }
    static void fixed(Map<String, Object> values, String key, Object expected) {
        Object actual = values.get(key);
        if (actual != null && !expected.toString().equals(actual.toString())
                && !(actual instanceof Number a && expected instanceof Number b && a.doubleValue() == b.doubleValue())) throw failed();
    }
    /** 计时关闭覆盖响应体读取，避免 ofInputStream 的读取越过请求期限。 */
    static void exchange(ModelRuntimeSpec runtime, HttpRequest request, Consumer<InputStream> consume) throws Exception {
        long start = System.nanoTime();
        var response = client(runtime).send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            long remaining = runtime.runtimePolicy().readTimeoutMs() - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (remaining <= 0) throw failed();
            var deadline = DEADLINES.schedule(() -> { try { input.close(); } catch (IOException ignored) { } }, remaining, TimeUnit.MILLISECONDS);
            try {
                if (response.statusCode() != 200) throw httpFailure(response.statusCode(), input);
                consume.accept(input);
            } finally { deadline.cancel(false); }
        }
    }
    static JsonNode json(InputStream input) {
        try {
            byte[] bytes = input.readNBytes(1024 * 1024 + 1);
            if (bytes.length > 1024 * 1024) throw failed(); return JSON.readTree(bytes);
        } catch (IOException invalid) { throw failed(); }
    }
    /** SSE data 以事件为单位解析；限制行、事件及整个响应，不将 URL 当音频。 */
    static void events(InputStream input, Consumer<JsonNode> consume) {
        try {
            var reader = new InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8);
            StringBuilder line = new StringBuilder(), event = new StringBuilder(); int total = 0, value;
            while ((value = reader.read()) != -1) {
                if (++total > 24 * 1024 * 1024 || line.length() > 12 * 1024 * 1024) throw failed();
                if (value != '\n') { if (value != '\r') line.append((char) value); continue; }
                if (line.isEmpty()) {
                    dispatch(event, consume); event.setLength(0);
                } else if (line.toString().startsWith("data:")) {
                    if (!event.isEmpty()) event.append('\n'); event.append(line.substring(5).stripLeading());
                    if (event.length() > 12 * 1024 * 1024) throw failed();
                }
                line.setLength(0);
            }
            // 未以空行结束的事件属于截断，不接受其中的完成标记。
        } catch (IOException invalid) { throw failed(); }
    }
    private static void dispatch(StringBuilder event, Consumer<JsonNode> consume) throws IOException {
        if (!event.isEmpty() && !"[DONE]".contentEquals(event)) consume.accept(JSON.readTree(event.toString()));
    }
    static void append(ByteArrayOutputStream target, JsonNode value) {
        if (value == null || value.isNull() || value.asText().isEmpty()) return;
        if (!value.isTextual() || value.textValue().length() > (MAX_AUDIO * 4L / 3 + 8)) throw failed();
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(value.textValue()); } catch (IllegalArgumentException invalid) { throw failed(); }
        if (target.size() + decoded.length > MAX_AUDIO) throw failed(); target.writeBytes(decoded);
    }
    static byte[] wav(byte[] pcm, int rate) {
        if (pcm.length == 0 || (pcm.length & 1) != 0 || pcm.length > MAX_AUDIO - 44) throw failed();
        var result = ByteBuffer.allocate(pcm.length + 44).order(ByteOrder.LITTLE_ENDIAN);
        result.put("RIFF".getBytes()).putInt(pcm.length + 36).put("WAVEfmt ".getBytes()).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(pcm.length).put(pcm); return result.array();
    }
    static ServiceException httpFailure(int status) { return new ServiceException("NATIVE_SPEECH_HTTP_" + status + ": 语音供应商请求未成功"); }
    /** 仅保留已知业务码及本地提示；不透出供应商正文、请求标识或任意错误字符串。 */
    static ServiceException httpFailure(int status, InputStream input) {
        try {
            byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) return httpFailure(status);
            JsonNode error = JSON.readTree(bytes);
            String code = error != null && error.path("code").isTextual() ? error.path("code").textValue() : "";
            String hint = switch (code) {
                case "AllocationQuota.FreeTierOnly" -> "该模型免费额度受限，请检查免费额度与用完即停设置";
                case "insufficient_quota", "QuotaExceeded" -> "该模型可用额度不足";
                case "AccessDenied.Unpurchased" -> "该模型服务未开通或账户状态不满足调用条件";
                case "Model.AccessDenied" -> "当前连接无权调用该模型";
                case "AccessDenied" -> "该模型当前不可访问，请检查权限或可用状态";
                case "InvalidApiKey", "invalid_api_key" -> "语音连接凭据无效";
                case "ModelNotFound" -> "语音模型不存在或当前连接不可访问";
                case "Throttling.RateQuota", "Throttling.AllocationQuota", "rate_limit_exceeded" -> "语音供应商请求受到限流";
                default -> null;
            };
            if (hint != null) return new ServiceException("NATIVE_SPEECH_HTTP_" + status + " [" + code + "]: " + hint);
        } catch (IOException invalid) { /* 错误正文不可读时仍保留确定的 HTTP 状态。 */ }
        return httpFailure(status);
    }
    static ServiceException businessFailure(int code) { return new ServiceException("NATIVE_SPEECH_CODE_" + code + ": 语音供应商业务请求未成功"); }
    static ServiceException failed() { return new ServiceException("NATIVE_SPEECH_PROVIDER_FAILED: 原生语音调用失败或返回不完整"); }
    private NativeSpeechSupport() { }
}
