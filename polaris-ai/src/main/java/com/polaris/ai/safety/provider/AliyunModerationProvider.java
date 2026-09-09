package com.polaris.ai.safety.provider;

import com.alibaba.fastjson2.JSON;
import com.aliyun.green20220302.models.TextModerationPlusRequest;
import com.aliyun.green20220302.models.TextModerationPlusResponse;
import com.aliyun.green20220302.models.TextModerationPlusResponseBody;
import com.aliyun.green20220302.models.TextModerationPlusResponseBody.TextModerationPlusResponseBodyData;
import com.aliyun.green20220302.models.TextModerationPlusResponseBody.TextModerationPlusResponseBodyDataResult;
import com.aliyun.teautil.models.RuntimeOptions;
import com.polaris.ai.safety.dto.ProviderResult;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.model.ProviderDecision;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Aliyun TextModerationPlus adapter with conservative response normalization. */
public final class AliyunModerationProvider implements ModerationProvider {
    private final AliyunClient client;
    private final String service;

    public AliyunModerationProvider(AliyunClient client, String service) {
        this.client = Objects.requireNonNull(client, "client");
        this.service = requireText(service, "service");
    }

    @Override
    public ProviderResult review(ModerationScene scene, String text, String requestId) {
        return review(scene, text, requestId, 1500);
    }

    @Override
    public ProviderResult review(
            ModerationScene scene, String text, String requestId, int timeoutMs) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(text, "text");
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }
        Instant started = Instant.now();
        AliyunModerationPayload payload;
        try {
            payload = client.moderate(
                    service, text, UUID.randomUUID().toString(), timeoutMs);
        } catch (Exception failure) {
            return unavailable(Duration.between(started, Instant.now()).toMillis());
        }
        ProviderResult mapped = map(payload);
        return new ProviderResult(mapped.provider(), mapped.decision(), mapped.riskScore(),
                mapped.categories(), mapped.providerRequestId(),
                Duration.between(started, Instant.now()).toMillis());
    }

    ProviderResult map(AliyunModerationPayload payload) {
        if (payload == null) {
            return unavailable(null, 0L, Set.of());
        }
        String providerRequestId = safeRequestId(payload.requestId());
        if (!Integer.valueOf(200).equals(payload.statusCode())
                || !Integer.valueOf(200).equals(payload.bodyCode())) {
            return unavailable(providerRequestId, 0L, Set.of());
        }
        if (providerRequestId == null || payload.results() == null
                || payload.results().isEmpty()) {
            return unavailable(providerRequestId, 0L, Set.of());
        }

        ProviderDecision strongest = ProviderDecision.PASS;
        boolean invalid = false;
        int maximumConfidence = 0;
        Set<String> categories = new LinkedHashSet<>();
        for (AliyunLabelResult item : payload.results()) {
            if (item == null || item.label() == null || item.label().isBlank()
                    || !validConfidence(item.confidence())) {
                invalid = true;
                continue;
            }
            String label = item.label().trim().toLowerCase(Locale.ROOT);
            ProviderDecision decision = labelDecision(label);
            if (decision == ProviderDecision.UNAVAILABLE) {
                invalid = true;
                continue;
            }
            categories.add(label);
            maximumConfidence = Math.max(maximumConfidence,
                    (int) Math.round(item.confidence()));
            if (decision == ProviderDecision.HIGH_RISK) {
                strongest = ProviderDecision.HIGH_RISK;
            } else if (decision == ProviderDecision.SUSPECT
                    && strongest != ProviderDecision.HIGH_RISK) {
                strongest = ProviderDecision.SUSPECT;
            }
        }
        if (strongest != ProviderDecision.HIGH_RISK && invalid) {
            return unavailable(providerRequestId, 0L, categories);
        }
        return new ProviderResult("aliyun", strongest, maximumConfidence,
                categories, providerRequestId, 0L);
    }

    private ProviderResult unavailable(long latencyMs) {
        return unavailable(null, latencyMs, Set.of());
    }

    private ProviderResult unavailable(
            String providerRequestId, long latencyMs, Set<String> categories) {
        return new ProviderResult("aliyun", ProviderDecision.UNAVAILABLE,
                null, categories, providerRequestId, latencyMs);
    }

    private static ProviderDecision labelDecision(String label) {
        return switch (label) {
            case "pass", "nonrisk" -> ProviderDecision.PASS;
            case "highrisk", "risk", "block" -> ProviderDecision.HIGH_RISK;
            case "review", "suspect" -> ProviderDecision.SUSPECT;
            default -> ProviderDecision.UNAVAILABLE;
        };
    }

    private static boolean validConfidence(Double confidence) {
        return confidence != null && Double.isFinite(confidence)
                && confidence >= 0 && confidence <= 100;
    }

    private static String safeRequestId(String requestId) {
        return requestId == null || requestId.isBlank() || requestId.length() > 128
                ? null : requestId;
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public String name() {
        return "aliyun";
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    @FunctionalInterface
    public interface AliyunClient {
        AliyunModerationPayload moderate(
                String service, String content, String requestContext,
                int timeoutMs) throws Exception;
    }

    /** Production adapter; its SDK client is constructed once by configuration. */
    public static final class SdkAliyunClient implements AliyunClient {
        private final com.aliyun.green20220302.Client client;

        public SdkAliyunClient(com.aliyun.green20220302.Client client) {
            this.client = Objects.requireNonNull(client, "client");
        }

        @Override
        public AliyunModerationPayload moderate(
                String service, String content, String requestContext,
                int timeoutMs) throws Exception {
            String parameters = JSON.toJSONString(Map.of(
                    "content", content,
                    "dataId", requestContext));
            RuntimeOptions runtimeOptions = new RuntimeOptions()
                    .setConnectTimeout(timeoutMs)
                    .setReadTimeout(timeoutMs);
            TextModerationPlusResponse response = client.textModerationPlusWithOptions(
                    new TextModerationPlusRequest()
                            .setService(service)
                            .setServiceParameters(parameters), runtimeOptions);
            return extract(response);
        }

        private static AliyunModerationPayload extract(TextModerationPlusResponse response) {
            if (response == null) {
                return new AliyunModerationPayload(null, null, null, null);
            }
            TextModerationPlusResponseBody body = response.getBody();
            TextModerationPlusResponseBodyData data = body == null ? null : body.getData();
            List<TextModerationPlusResponseBodyDataResult> results =
                    data == null ? null : data.getResult();
            List<AliyunLabelResult> projected = null;
            if (results != null) {
                projected = new ArrayList<>(results.size());
                for (TextModerationPlusResponseBodyDataResult result : results) {
                    projected.add(result == null
                            ? new AliyunLabelResult(null, null)
                            : new AliyunLabelResult(result.getLabel(),
                            result.getConfidence() == null
                                    ? null : result.getConfidence().doubleValue()));
                }
            }
            return new AliyunModerationPayload(
                    response.getStatusCode(),
                    body == null ? null : body.getCode(),
                    body == null ? null : body.getRequestId(),
                    projected);
        }
    }
}
