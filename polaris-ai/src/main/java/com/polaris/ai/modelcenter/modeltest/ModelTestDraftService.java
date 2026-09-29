package com.polaris.ai.modelcenter.modeltest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.modelcenter.client.ProviderCallException;
import com.polaris.ai.modelcenter.client.ProviderRuntimeContext;
import com.polaris.ai.modelcenter.schema.CapabilityConfigProcessor;
import com.polaris.ai.modelcenter.schema.ResolvedCapabilitySchema;
import com.polaris.ai.modelcenter.schema.SchemaProfileResolver;
import com.polaris.ai.modelcenter.service.IAiProviderConnectionService;
import com.polaris.ai.modelcenter.vo.ProviderConnectionRuntime;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/** 保存前模型测试编排：只信任连接 ID 与后端 Registry 中的 Schema/Profile。 */
@Service
public class ModelTestDraftService {

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");
    private static final int MAX_CONFIG_BYTES = 65_536;

    private final IAiProviderConnectionService connectionService;
    private final SchemaProfileResolver schemaResolver;
    private final CapabilityConfigProcessor configProcessor;
    private final CapabilityTestHandlerRegistry handlerRegistry;
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    public ModelTestDraftService(
            IAiProviderConnectionService connectionService,
            SchemaProfileResolver schemaResolver,
            CapabilityConfigProcessor configProcessor,
            CapabilityTestHandlerRegistry handlerRegistry) {
        this.connectionService = connectionService;
        this.schemaResolver = schemaResolver;
        this.configProcessor = configProcessor;
        this.handlerRegistry = handlerRegistry;
    }

    public CapabilityTestResult test(ModelTestDraftRequest request) {
        validateRequest(request);
        String capabilityCode = normalizeCode(request.capabilityCode());
        String testCapability = normalizeCode(request.testCapability());
        ProviderConnectionRuntime connection = connectionService.getRuntime(
                request.connectionId());
        ResolvedCapabilitySchema resolved = schemaResolver.resolve(
                capabilityCode, request.schemaVersion(),
                connection.protocolCode(), connection.providerCode(),
                request.modelName().trim());
        JsonNode userConfig = objectMapper.valueToTree(
                request.capabilityConfig());
        if (userConfig.toString().getBytes(StandardCharsets.UTF_8).length
                > MAX_CONFIG_BYTES) {
            throw new ServiceException("CAPABILITY_CONFIG_INVALID: 内容过大");
        }
        ObjectNode normalized = configProcessor.process(
                resolved.definition(), userConfig);
        ModelRuntimePolicyDraft policy = request.runtimePolicy() == null
                ? ModelRuntimePolicyDraft.empty() : request.runtimePolicy();
        validatePolicy(policy);
        ModelTestContext context = new ModelTestContext(
                ProviderRuntimeContext.from(connection),
                request.modelName().trim(), resolved, normalized,
                policy, testCapability);
        try {
            return handlerRegistry.getRequired(capabilityCode).test(context);
        } catch (ProviderCallException error) {
            throw new ServiceException(providerErrorMessage(error));
        }
    }

    private String providerErrorMessage(ProviderCallException error) {
        return switch (error.errorType()) {
            case AUTH_FAILED -> "服务商拒绝了 API 密钥，请检查密钥是否有效";
            case QUOTA_EXHAUSTED -> "服务商额度不足，请充值或调整额度设置后重试";
            case RATE_LIMITED -> "服务商请求过于频繁，请稍后重试";
            case MODEL_NOT_FOUND -> "服务商未找到该模型，请重新选择远程模型";
            case REQUEST_INVALID -> "服务商不接受当前模型参数，请调整能力配置后重试";
            case PROVIDER_TIMEOUT -> "服务商响应超时，请稍后重试";
            case DNS_FAILED -> "无法解析服务商域名，请检查服务地址或网络设置";
            case CONNECTION_REFUSED -> "服务商连接被拒绝，请检查服务地址及代理是否可用";
            case TLS_FAILED -> "服务商 HTTPS 证书校验失败，请检查服务地址和证书配置";
            case PROVIDER_UNAVAILABLE -> "暂时无法连接服务商，请检查地址或稍后重试";
            case CONTENT_REJECTED -> "测试内容被服务商安全策略拒绝";
            case UNKNOWN -> "服务商调用失败，请检查连接与模型配置";
        };
    }

    private void validateRequest(ModelTestDraftRequest request) {
        if (request == null || request.connectionId() == null
                || request.connectionId() <= 0) {
            throw new ServiceException("MODEL_TEST_CONNECTION_REQUIRED");
        }
        requireText(request.modelName(), 256, "MODEL_TEST_MODEL_REQUIRED");
        requireCode(request.capabilityCode(), "MODEL_TEST_CAPABILITY_INVALID");
        requireCode(request.testCapability(), "MODEL_TEST_TYPE_INVALID");
        if (request.schemaVersion() == null || request.schemaVersion() < 1) {
            throw new ServiceException("MODEL_TEST_SCHEMA_VERSION_INVALID");
        }
        if (request.capabilityConfig().size() > 128) {
            throw new ServiceException("CAPABILITY_CONFIG_INVALID: 字段过多");
        }
    }

    private void requireText(String value, int maxLength, String errorCode) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new ServiceException(errorCode);
        }
    }

    private void requireCode(String value, String errorCode) {
        if (value == null || value.isBlank()
                || !CODE_PATTERN.matcher(normalizeCode(value)).matches()) {
            throw new ServiceException(errorCode);
        }
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void validatePolicy(ModelRuntimePolicyDraft policy) {
        requireRange(policy.maxConcurrency(), 1, 10_000,
                "maxConcurrency");
        requireRange(policy.connectTimeoutMs(), 100, 60_000,
                "connectTimeoutMs");
        requireRange(policy.readTimeoutMs(), 100, 300_000,
                "readTimeoutMs");
        requireRange(policy.retryCount(), 0, 10, "retryCount");
        requireRange(policy.priority(), -10_000, 10_000, "priority");
        BigDecimal qps = policy.qpsLimit();
        if (qps != null && (qps.compareTo(BigDecimal.ZERO) <= 0
                || qps.compareTo(new BigDecimal("10000")) > 0)) {
            throw new ServiceException(
                    "MODEL_TEST_RUNTIME_POLICY_INVALID: qpsLimit");
        }
    }

    private void requireRange(
            Integer value,
            int minimum,
            int maximum,
            String field) {
        if (value != null && (value < minimum || value > maximum)) {
            throw new ServiceException(
                    "MODEL_TEST_RUNTIME_POLICY_INVALID: " + field);
        }
    }
}
