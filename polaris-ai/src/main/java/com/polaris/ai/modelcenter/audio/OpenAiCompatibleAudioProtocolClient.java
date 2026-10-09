package com.polaris.ai.modelcenter.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.modelcenter.protocol.ProtocolAdapterRegistry;
import com.polaris.ai.modelcenter.protocol.ProtocolEndpoint;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.audio.*;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import com.polaris.ai.runtime.usage.UsageSource;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** OpenAI-compatible Audio HTTP 映射；端点仍由代码级 Protocol Adapter 固定。 */
@Component
class OpenAiCompatibleAudioProtocolClient implements AudioProtocolClient {

    private static final int MAX_AUDIO_RESPONSE_BYTES = 50 * 1024 * 1024;
    private final ProtocolAdapterRegistry protocolRegistry;
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    OpenAiCompatibleAudioProtocolClient(ProtocolAdapterRegistry protocolRegistry) {
        this.protocolRegistry = protocolRegistry;
    }

    @Override
    public boolean supports(String protocolCode) {
        return "OPENAI_COMPATIBLE".equalsIgnoreCase(protocolCode);
    }

    @Override
    public AudioProtocolResult<AudioTtsResult> synthesize(
            ModelRuntimeSpec runtime,
            AudioTtsInvocation invocation) {
        try {
            Map<String, Object> parameters = merged(
                    runtime.invocationParameters(), invocation.overrides());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", runtime.modelName());
            body.put("input", invocation.text());
            body.put("voice", required(parameters, "voice"));
            body.put("response_format", parameters.getOrDefault("format", "mp3"));
            put(body, "speed", parameters.get("speed"));
            HttpRequest request = baseRequest(runtime, "AUDIO_TTS")
                    .header("Content-Type", "application/json")
                    .header("Accept", "audio/*")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> response = client(runtime).send(
                    request, HttpResponse.BodyHandlers.ofByteArray());
            requireSuccess(response.statusCode(), response.body().length);
            if (response.body().length > MAX_AUDIO_RESPONSE_BYTES) {
                throw new ServiceException("AUDIO_RESPONSE_TOO_LARGE");
            }
            String contentType = response.headers().firstValue("content-type")
                    .orElse("audio/" + parameters.getOrDefault("format", "mpeg"));
            Integer sampleRate = integer(parameters.get("sampleRate"));
            NormalizedUsage usage = new NormalizedUsage(
                    null, null, null, (long) invocation.text().length(),
                    null, null, Map.of(), UsageSource.CHAR_ESTIMATED);
            return new AudioProtocolResult<>(
                    new AudioTtsResult(response.body(), contentType, sampleRate),
                    usage, requestId(response));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("AUDIO_TTS_PROVIDER_FAILED: " + e.getMessage());
        }
    }

    @Override
    public AudioProtocolResult<AudioSttResult> transcribe(
            ModelRuntimeSpec runtime,
            AudioSttInvocation invocation) {
        String boundary = "polaris-" + UUID.randomUUID();
        try {
            Map<String, Object> parameters = merged(
                    runtime.invocationParameters(), invocation.overrides());
            AudioInput audio = invocation.audio();
            byte[] prefix = multipartPrefix(
                    boundary, runtime.modelName(), parameters, audio)
                    .getBytes(StandardCharsets.UTF_8);
            byte[] suffix = ("\r\n--" + boundary + "--\r\n")
                    .getBytes(StandardCharsets.UTF_8);
            HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.concat(
                    HttpRequest.BodyPublishers.ofByteArray(prefix),
                    HttpRequest.BodyPublishers.ofInputStream(audio::openStream),
                    HttpRequest.BodyPublishers.ofByteArray(suffix));
            HttpRequest request = baseRequest(runtime, "AUDIO_STT")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "application/json")
                    .POST(publisher).build();
            HttpResponse<InputStream> response = client(runtime).send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (InputStream input = response.body()) {
                bytes = input.readNBytes(4 * 1024 * 1024 + 1);
            }
            requireSuccess(response.statusCode(), bytes.length);
            if (bytes.length > 4 * 1024 * 1024) {
                throw new ServiceException("AUDIO_TRANSCRIPT_TOO_LARGE");
            }
            JsonNode json = objectMapper.readTree(bytes);
            AudioSttResult result = new AudioSttResult(
                    json.path("text").asText(""), segments(json.path("segments")));
            return new AudioProtocolResult<>(result,
                    NormalizedUsage.unknown(), requestId(response));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("AUDIO_STT_PROVIDER_FAILED: " + e.getMessage());
        }
    }

    private HttpRequest.Builder baseRequest(
            ModelRuntimeSpec runtime, String capability) {
        ProtocolEndpoint endpoint = protocolRegistry
                .getRequired(runtime.protocolCode())
                .findEndpoint(capability)
                .orElseThrow(() -> new ServiceException(
                        "PROTOCOL_CAPABILITY_NOT_MAPPED: " + capability));
        String base = runtime.baseUrl().endsWith("/")
                ? runtime.baseUrl().substring(0, runtime.baseUrl().length() - 1)
                : runtime.baseUrl();
        URI uri = URI.create(base + endpoint.relativePath());
        Object credential = runtime.credentials().get("apiKey");
        if (credential == null || credential.toString().isBlank()) {
            throw new ServiceException("PROVIDER_CREDENTIAL_REQUIRED");
        }
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(runtime.runtimePolicy().readTimeoutMs()))
                .header("Authorization", "Bearer " + credential);
    }

    private HttpClient client(ModelRuntimeSpec runtime) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(
                        runtime.runtimePolicy().connectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private String multipartPrefix(
            String boundary,
            String model,
            Map<String, Object> parameters,
            AudioInput audio) {
        StringBuilder builder = new StringBuilder();
        field(builder, boundary, "model", model);
        field(builder, boundary, "response_format",
                Boolean.TRUE.equals(parameters.get("timestamps"))
                        ? "verbose_json" : "json");
        putField(builder, boundary, "language", parameters.get("language"));
        builder.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(safeFileName(audio.fileName())).append("\"\r\n")
                .append("Content-Type: ").append(audio.contentType()).append("\r\n\r\n");
        return builder.toString();
    }

    private void field(StringBuilder builder, String boundary, String name, Object value) {
        builder.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(name)
                .append("\"\r\n\r\n").append(value).append("\r\n");
    }

    private void putField(StringBuilder builder, String boundary, String name, Object value) {
        if (value != null && !value.toString().isBlank()) field(builder, boundary, name, value);
    }

    private List<AudioTranscriptSegment> segments(JsonNode source) {
        if (!source.isArray()) return List.of();
        List<AudioTranscriptSegment> result = new ArrayList<>();
        for (JsonNode item : source) {
            result.add(new AudioTranscriptSegment(
                    Math.round(item.path("start").asDouble() * 1000),
                    Math.round(item.path("end").asDouble() * 1000),
                    item.hasNonNull("speaker") ? item.path("speaker").asText() : null,
                    item.path("text").asText("")));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> merged(Map<String, Object> base, Map<String, Object> overrides) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        result.putAll(overrides);
        return result;
    }

    private Object required(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ServiceException("AUDIO_CONFIG_REQUIRED: " + key);
        }
        return value;
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String safeFileName(String value) {
        return value == null ? "audio.bin"
                : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void requireSuccess(int status, int responseBytes) {
        if (status < 200 || status >= 300) {
            throw new ServiceException("AUDIO_PROVIDER_HTTP_" + status);
        }
        if (responseBytes <= 0) throw new ServiceException("AUDIO_PROVIDER_EMPTY_RESPONSE");
    }

    private String requestId(HttpResponse<?> response) {
        return response.headers().firstValue("x-request-id").orElse(null);
    }
}
