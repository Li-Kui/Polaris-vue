package com.polaris.ai.modelcenter.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.modelcenter.client.*;
import com.polaris.ai.modelcenter.protocol.OpenAiCompatibleProtocolAdapter;
import com.polaris.ai.modelcenter.protocol.ProtocolEndpoint;
import com.polaris.ai.modelcenter.vo.ProviderConnectionRuntime;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OpenAI 协议族 /models 发现；仅解析身份，不推断模型能力。 */
@Component
public class OpenAiCompatibleRemoteModelDiscovery
        implements RemoteModelDiscovery {

    static final int MAX_RESPONSE_BYTES = 1_048_576;
    static final int MAX_MODELS = 500;

    private final ProviderHttpClientFactory clientFactory;
    private final ProviderErrorTranslator errorTranslator;
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    public OpenAiCompatibleRemoteModelDiscovery(
            ProviderHttpClientFactory clientFactory,
            ProviderErrorTranslator errorTranslator) {
        this.clientFactory = clientFactory;
        this.errorTranslator = errorTranslator;
    }

    @Override
    public String protocolCode() {
        return OpenAiCompatibleProtocolAdapter.PROTOCOL_CODE;
    }

    @Override
    public List<RemoteModelInfo> listModels(
            ProviderConnectionRuntime connection,
            ProtocolEndpoint endpoint) {
        ProviderHttpClient client = clientFactory.get(
                ProviderRuntimeContext.from(connection));
        ProviderHttpResponse response;
        try {
            response = client.execute(ProviderHttpRequest.get(
                    endpoint, Duration.ofSeconds(10), MAX_RESPONSE_BYTES));
        } catch (ProviderCallException e) {
            throw discoveryException(e.errorType());
        }
        validateStatus(response.statusCode());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw discoveryException(
                    errorTranslator.fromResponse(response).errorType());
        }
        return parse(response.body());
    }

    private void validateStatus(int status) {
        if (status >= 200 && status < 300) {
            return;
        }
        if (status == 401 || status == 403) {
            throw new ServiceException("MODEL_DISCOVERY_AUTH_FAILED");
        }
        if (status == 404 || status == 405 || status == 501) {
            throw new ServiceException("MODEL_DISCOVERY_NOT_SUPPORTED");
        }
        if (status == 429) {
            throw new ServiceException("MODEL_DISCOVERY_RATE_LIMITED");
        }
    }

    private ServiceException discoveryException(ProviderErrorType type) {
        return switch (type) {
            case AUTH_FAILED ->
                    new ServiceException("MODEL_DISCOVERY_AUTH_FAILED");
            case RATE_LIMITED ->
                    new ServiceException("MODEL_DISCOVERY_RATE_LIMITED");
            case PROVIDER_TIMEOUT ->
                    new ServiceException("MODEL_DISCOVERY_TIMEOUT");
            case DNS_FAILED ->
                    new ServiceException("MODEL_DISCOVERY_DNS_FAILED");
            case CONNECTION_REFUSED ->
                    new ServiceException("MODEL_DISCOVERY_CONNECTION_REFUSED");
            case TLS_FAILED ->
                    new ServiceException("MODEL_DISCOVERY_TLS_FAILED");
            default -> new ServiceException("MODEL_DISCOVERY_UNAVAILABLE");
        };
    }

    private List<RemoteModelInfo> parse(String body) {
        try {
            JsonNode data = objectMapper.readTree(body).path("data");
            if (!data.isArray()) {
                throw new ServiceException("MODEL_DISCOVERY_RESPONSE_INVALID");
            }
            if (data.size() > MAX_MODELS) {
                throw new ServiceException("MODEL_DISCOVERY_MODEL_LIMIT_EXCEEDED");
            }
            Map<String, RemoteModelInfo> unique = new LinkedHashMap<>();
            for (JsonNode model : data) {
                String id = text(model, "id", 256, true);
                String ownedBy = text(model, "owned_by", 100, false);
                if (id != null) {
                    unique.putIfAbsent(id, new RemoteModelInfo(id, ownedBy));
                }
            }
            return unique.values().stream()
                    .sorted(Comparator.comparing(RemoteModelInfo::id))
                    .toList();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("MODEL_DISCOVERY_RESPONSE_INVALID");
        }
    }

    private String text(
            JsonNode object,
            String field,
            int maxLength,
            boolean required) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            if (required) {
                throw new ServiceException("MODEL_DISCOVERY_RESPONSE_INVALID");
            }
            return null;
        }
        if (!value.isTextual()) {
            throw new ServiceException("MODEL_DISCOVERY_RESPONSE_INVALID");
        }
        String text = value.textValue().trim();
        if (text.isEmpty() || text.length() > maxLength
                || text.codePoints().anyMatch(Character::isISOControl)) {
            throw new ServiceException("MODEL_DISCOVERY_RESPONSE_INVALID");
        }
        return text;
    }
}
