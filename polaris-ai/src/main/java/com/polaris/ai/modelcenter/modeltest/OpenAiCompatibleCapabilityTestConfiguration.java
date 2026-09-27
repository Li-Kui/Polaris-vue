package com.polaris.ai.modelcenter.modeltest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.modelcenter.audio.AudioProtocolClient;
import com.polaris.ai.modelcenter.audio.AudioProtocolResult;
import com.polaris.ai.modelcenter.client.ProviderErrorTranslator;
import com.polaris.ai.modelcenter.client.ProviderHttpClientFactory;
import com.polaris.ai.modelcenter.client.ProviderHttpRequest;
import com.polaris.ai.modelcenter.client.ProviderHttpResponse;
import com.polaris.ai.modelcenter.protocol.ProtocolAdapterRegistry;
import com.polaris.ai.modelcenter.protocol.ProtocolEndpoint;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.RuntimePolicySpec;
import com.polaris.ai.runtime.audio.*;
import com.polaris.common.exception.ServiceException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 真实 Provider 请求驱动的 Draft Model Test Handler。 */
@Configuration(proxyBeanMethods = false)
public class OpenAiCompatibleCapabilityTestConfiguration {

    @Bean
    CapabilityTestHandler chatCompletionTestHandler(
            ProtocolAdapterRegistry protocols,
            ProviderHttpClientFactory clients,
            ProviderErrorTranslator errors) {
        return new JsonCapabilityTestHandler(
                "CHAT_COMPLETION", protocols, clients, errors) {
            @Override
            Map<String, Object> request(ModelTestContext context) {
                Map<String, Object> body = base(context);
                body.put("messages", List.of(Map.of(
                        "role", "user",
                        "content", "Reply with exactly OK")));
                body.put("stream", false);
                put(body, "max_tokens", value(context, "maxTokens"));
                put(body, "temperature", value(context, "temperature"));
                return body;
            }

            @Override
            boolean valid(JsonNode response) {
                if (!response.path("choices").isArray()
                        || response.path("choices").isEmpty()) {
                    return false;
                }
                JsonNode message = response.path("choices").path(0).path("message");
                boolean hasContent = message.path("content").isTextual()
                        && !message.path("content").asText().isBlank();
                boolean hasTools = message.path("tool_calls").isArray()
                        && !message.path("tool_calls").isEmpty();
                return hasContent || hasTools;
            }
        };
    }

    @Bean
    CapabilityTestHandler textEmbeddingTestHandler(
            ProtocolAdapterRegistry protocols,
            ProviderHttpClientFactory clients,
            ProviderErrorTranslator errors) {
        return new JsonCapabilityTestHandler(
                "TEXT_EMBEDDING", protocols, clients, errors) {
            @Override
            Map<String, Object> request(ModelTestContext context) {
                Map<String, Object> body = base(context);
                body.put("input", List.of("Polaris model test"));
                if ("REQUEST".equals(value(context, "dimensionMode"))) {
                    put(body, "dimensions", value(context, "dimension"));
                }
                return body;
            }

            @Override
            boolean valid(JsonNode response) {
                return response.path("data").isArray()
                        && !response.path("data").isEmpty()
                        && response.path("data").get(0)
                        .path("embedding").isArray();
            }
        };
    }

    @Bean
    CapabilityTestHandler imageGenerationTestHandler(
            ProtocolAdapterRegistry protocols,
            ProviderHttpClientFactory clients,
            ProviderErrorTranslator errors) {
        return new JsonCapabilityTestHandler(
                "IMAGE_GENERATION", protocols, clients, errors) {
            @Override
            Map<String, Object> request(ModelTestContext context) {
                Map<String, Object> body = base(context);
                body.put("prompt", "A simple blue circle on white background");
                body.put("n", 1);
                put(body, "size", value(context, "size"));
                put(body, "quality", value(context, "quality"));
                return body;
            }

            @Override
            boolean valid(JsonNode response) {
                return response.path("data").isArray()
                        && !response.path("data").isEmpty();
            }
        };
    }

    @Bean
    CapabilityTestHandler audioTtsTestHandler(
            List<AudioProtocolClient> clients) {
        return new AudioCapabilityTestHandler("AUDIO_TTS", clients) {
            @Override
            CapabilityTestResult invoke(
                    AudioProtocolClient client, ModelRuntimeSpec runtime) {
                AudioProtocolResult<AudioTtsResult> result = client.synthesize(
                        runtime, new AudioTtsInvocation(
                                "Polaris audio test", false, Map.of()));
                if (result.value().audio().length == 0) {
                    throw new ServiceException("MODEL_TEST_EMPTY_AUDIO");
                }
                return success(result.providerRequestId(), Map.of(
                        "audioBytes", result.value().audio().length,
                        "contentType", result.value().contentType()));
            }
        };
    }

    @Bean
    CapabilityTestHandler audioSttTestHandler(
            List<AudioProtocolClient> clients) {
        return new AudioCapabilityTestHandler("AUDIO_STT", clients) {
            @Override
            CapabilityTestResult invoke(
                    AudioProtocolClient client, ModelRuntimeSpec runtime) {
                AudioProtocolResult<AudioSttResult> result = client.transcribe(
                        runtime, new AudioSttInvocation(
                                new ByteArrayAudioInput(
                                        silentWav(), "polaris-test.wav", "audio/wav"),
                                Map.of()));
                return success(result.providerRequestId(), Map.of(
                        "transcriptCharacters", result.value().text().length(),
                        "segments", result.value().segments().size()));
            }
        };
    }

    private abstract static class JsonCapabilityTestHandler
            implements CapabilityTestHandler {

        private static final int MAX_REQUEST_BYTES = 65_536;
        private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
        private final String capabilityCode;
        private final ProtocolAdapterRegistry protocols;
        private final ProviderHttpClientFactory clients;
        private final ProviderErrorTranslator errors;
        private final ObjectMapper objectMapper =
                new ObjectMapper().findAndRegisterModules();

        private JsonCapabilityTestHandler(
                String capabilityCode,
                ProtocolAdapterRegistry protocols,
                ProviderHttpClientFactory clients,
                ProviderErrorTranslator errors) {
            this.capabilityCode = capabilityCode;
            this.protocols = protocols;
            this.clients = clients;
            this.errors = errors;
        }

        @Override
        public String capabilityCode() {
            return capabilityCode;
        }

        @Override
        public CapabilityTestResult test(ModelTestContext context) {
            ProtocolEndpoint endpoint = protocols
                    .getRequired(context.provider().protocolCode())
                    .findEndpoint(capabilityCode)
                    .orElseThrow(() -> new ServiceException(
                            "PROTOCOL_CAPABILITY_NOT_MAPPED: " + capabilityCode));
            try {
                ProviderHttpResponse response = clients.get(context.provider())
                        .execute(ProviderHttpRequest.post(
                                endpoint,
                                objectMapper.writeValueAsString(request(context)),
                                readTimeout(context),
                                MAX_REQUEST_BYTES,
                                MAX_RESPONSE_BYTES));
                if (response.statusCode() < 200
                        || response.statusCode() >= 300) {
                    throw errors.fromResponse(response);
                }
                JsonNode body = objectMapper.readTree(response.body());
                if (!valid(body)) {
                    throw new ServiceException("MODEL_TEST_RESPONSE_INVALID");
                }
                Map<String, Object> metrics = new LinkedHashMap<>();
                metrics.put("latencyMs", response.latencyMillis());
                if (response.providerRequestId() != null) {
                    metrics.put("providerRequestId",
                            response.providerRequestId());
                }
                return new CapabilityTestResult(
                        true, "MODEL_TEST_OK", "模型测试成功", metrics);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new ServiceException("MODEL_TEST_PROVIDER_FAILED");
            }
        }

        abstract Map<String, Object> request(ModelTestContext context);

        abstract boolean valid(JsonNode response);

        Map<String, Object> base(ModelTestContext context) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", context.modelName());
            return body;
        }

        Object value(ModelTestContext context, String key) {
            JsonNode value = context.capabilityConfig().get(key);
            return value == null || value.isNull()
                    ? null : objectMapper.convertValue(value, Object.class);
        }

        void put(Map<String, Object> body, String key, Object value) {
            if (value != null) {
                body.put(key, value);
            }
        }
    }

    private abstract static class AudioCapabilityTestHandler
            implements CapabilityTestHandler {

        private final String capabilityCode;
        private final List<AudioProtocolClient> clients;

        private AudioCapabilityTestHandler(
                String capabilityCode,
                List<AudioProtocolClient> clients) {
            this.capabilityCode = capabilityCode;
            this.clients = List.copyOf(clients);
        }

        @Override
        public String capabilityCode() {
            return capabilityCode;
        }

        @Override
        public CapabilityTestResult test(ModelTestContext context) {
            AudioProtocolClient client = clients.stream()
                    .filter(item -> item.supports(
                            context.provider().protocolCode()))
                    .findFirst()
                    .orElseThrow(() -> new ServiceException(
                            "AUDIO_PROTOCOL_NOT_SUPPORTED"));
            return invoke(client, runtime(context, capabilityCode));
        }

        abstract CapabilityTestResult invoke(
                AudioProtocolClient client, ModelRuntimeSpec runtime);
    }

    private static ModelRuntimeSpec runtime(
            ModelTestContext context, String capabilityCode) {
        Map<String, Object> parameters = new ObjectMapper()
                .convertValue(context.capabilityConfig(), Map.class);
        String hash = context.schema().runtimeSchemaHash();
        return new ModelRuntimeSpec(
                0L, "draft-test", 1L,
                context.provider().connectionId(),
                context.provider().connectionRevision(),
                context.provider().providerCode(),
                context.provider().protocolCode(),
                context.provider().networkMode(),
                context.provider().baseUrl(),
                context.modelName(), capabilityCode,
                parameters, Map.of(), Set.of(),
                context.provider().extraConfig(),
                context.provider().credentials(),
                runtimePolicy(context), context.schema().definition().schemaVersion(),
                hash, hash);
    }

    private static RuntimePolicySpec runtimePolicy(ModelTestContext context) {
        ModelRuntimePolicyDraft draft = context.runtimePolicy();
        return new RuntimePolicySpec(
                draft.maxConcurrency(),
                draft.connectTimeoutMs() == null
                        ? 10_000 : draft.connectTimeoutMs(),
                draft.readTimeoutMs() == null
                        ? 60_000 : draft.readTimeoutMs(),
                draft.retryCount() == null ? 0 : draft.retryCount(),
                draft.qpsLimit(),
                draft.priority() == null ? 0 : draft.priority());
    }

    private static Duration readTimeout(ModelTestContext context) {
        Integer millis = context.runtimePolicy().readTimeoutMs();
        return Duration.ofMillis(millis == null ? 60_000 : millis);
    }

    private static CapabilityTestResult success(
            String requestId, Map<String, Object> values) {
        Map<String, Object> metrics = new LinkedHashMap<>(values);
        if (requestId != null) {
            metrics.put("providerRequestId", requestId);
        }
        return new CapabilityTestResult(
                true, "MODEL_TEST_OK", "模型测试成功", metrics);
    }

    /** 100ms 16kHz/16-bit mono silence; small but valid streaming input. */
    private static byte[] silentWav() {
        int sampleRate = 16_000;
        int dataBytes = sampleRate / 10 * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataBytes)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{'R', 'I', 'F', 'F'});
        buffer.putInt(36 + dataBytes);
        buffer.put(new byte[]{'W', 'A', 'V', 'E'});
        buffer.put(new byte[]{'f', 'm', 't', ' '});
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * 2);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put(new byte[]{'d', 'a', 't', 'a'});
        buffer.putInt(dataBytes);
        return buffer.array();
    }
}
