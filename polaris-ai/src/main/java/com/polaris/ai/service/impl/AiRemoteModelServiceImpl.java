package com.polaris.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.dto.FetchModelsRequest;
import com.polaris.ai.service.IAiRemoteModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 远程模型列表拉取服务实现
 * 通过后端代理调用各提供商的 OpenAI 兼容 /models 端点，统一返回可用模型 ID 列表
 *
 * @author polaris
 */
@Slf4j
@Service
public class AiRemoteModelServiceImpl implements IAiRemoteModelService {

    /** 阿里通义 OpenAI 兼容地址 */
    private static final String DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    /** DeepSeek 官方地址 */
    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1";
    /** 火山引擎 Ark 默认地址 */
    private static final String ARK_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    /** Ollama 默认地址 */
    private static final String OLLAMA_DEFAULT_URL = "http://localhost:11434";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public List<String> fetchRemoteModels(FetchModelsRequest req) {
        String provider = req.getProvider().trim().toLowerCase();
        String resolvedUrl = resolveBaseUrl(provider, req.getBaseUrl());
        String apiKey = resolveApiKey(provider, req.getApiKey());

        List<String> candidateEndpoints = buildCandidateEndpoints(resolvedUrl);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // 依次尝试候选 endpoint，遇到 200 且有数据立即返回
        HttpResponse<String> lastResponse = null;
        for (String endpoint : candidateEndpoints) {
            log.info(">>> 尝试拉取远程模型列表, provider={}, endpoint={}", provider, endpoint);
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                lastResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (lastResponse.statusCode() == 401 || lastResponse.statusCode() == 403) {
                    throw new IllegalArgumentException("API Key 无效或已过期，请检查后重试");
                }

                if (lastResponse.statusCode() == 200) {
                    List<String> modelIds = parseModelIds(lastResponse.body());
                    if (!modelIds.isEmpty()) {
                        log.info(">>> 成功获取 {} 个模型, provider={}, endpoint={}", modelIds.size(), provider, endpoint);
                        return modelIds;
                    }
                }

                // 404 或空列表 → 继续尝试下一个候选 endpoint
                log.info(">>> endpoint={} 返回 status={}, 尝试下一个候选路径", endpoint, lastResponse.statusCode());

            } catch (IllegalArgumentException e) {
                throw e; // 鉴权失败直接抛出
            } catch (java.net.http.HttpTimeoutException e) {
                log.error("拉取模型列表超时, provider={}, endpoint={}", provider, endpoint, e);
                throw new RuntimeException("请求超时，请检查网络连接或 API Base URL 是否可达");
            } catch (Exception e) {
                log.error("拉取模型列表异常, provider={}, endpoint={}", provider, endpoint, e);
                // 非最后一个候选时继续尝试
                if (candidateEndpoints.indexOf(endpoint) < candidateEndpoints.size() - 1) {
                    continue;
                }
                throw new RuntimeException("获取模型列表失败：" + e.getMessage());
            }
        }

        // 所有候选 endpoint 均失败
        if (lastResponse != null && lastResponse.statusCode() != 200) {
            throw new RuntimeException("获取模型列表失败，HTTP 状态码：" + lastResponse.statusCode());
        }
        throw new RuntimeException("未获取到可用模型，请检查 API Key 权限或服务地址");
    }

    // ================================================================
    //  内部辅助方法
    // ================================================================

    /**
     * 智能构建候选模型列表 endpoint
     * 兼容带版本路径（/v1）和不带版本路径的 URL
     */
    private List<String> buildCandidateEndpoints(String resolvedUrl) {
        List<String> endpoints = new ArrayList<>();
        String normalizedUrl = resolvedUrl.endsWith("/")
                ? resolvedUrl.substring(0, resolvedUrl.length() - 1) : resolvedUrl;

        if (normalizedUrl.matches(".*?/v\\d+$")) {
            // URL 已包含版本路径如 /v1, /v2 → 直接追加 /models
            endpoints.add(normalizedUrl + "/models");
        } else {
            // URL 不含版本路径 → 先试 /v1/models，再试 /models
            endpoints.add(normalizedUrl + "/v1/models");
            endpoints.add(normalizedUrl + "/models");
        }
        return endpoints;
    }

    /**
     * 解析最终 Base URL
     * 核心原则：用户自定义 URL 永远优先（支持中转站/代理），provider 仅用于无 URL 时的默认兜底
     */
    private String resolveBaseUrl(String provider, String customBaseUrl) {
        boolean hasCustomUrl = customBaseUrl != null && !customBaseUrl.trim().isEmpty();

        // ① 用户提供了自定义 URL → 直接使用（支持任意中转站）
        if (hasCustomUrl) {
            String url = customBaseUrl.trim();
            // Ollama 特殊处理：如果 URL 不含 /v1，自动追加以兼容 OpenAI 协议
            if ("ollama".equalsIgnoreCase(provider) && !url.contains("/v1")) {
                return url + "/v1";
            }
            return url;
        }

        // ② 未提供自定义 URL → 使用提供商官方默认地址
        switch (provider) {
            case "deepseek":
                return DEEPSEEK_BASE_URL;
            case "dashscope":
                return DASHSCOPE_BASE_URL;
            case "ollama":
                return OLLAMA_DEFAULT_URL + "/v1";
            case "ark":
                return ARK_BASE_URL;
            default:
                throw new IllegalArgumentException("当前提供商无默认地址，请填写 API Base URL");
        }
    }

    /** Ollama 无需鉴权，使用固定占位 Key */
    private String resolveApiKey(String provider, String apiKey) {
        return "ollama".equals(provider) ? "ollama" : apiKey.trim();
    }

    /**
     * 解析 OpenAI 兼容格式的模型列表 JSON
     * 标准格式: { "data": [{ "id": "model-name", ... }, ...] }
     * 兜底格式: { "models": [{ "name": "...", ... }] } (Ollama 旧版)
     */
    private List<String> parseModelIds(String responseBody) {
        List<String> result = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);

            // 标准 OpenAI 格式: { "data": [...] }
            JsonNode dataNode = root.get("data");
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode model : dataNode) {
                    JsonNode idNode = model.get("id");
                    if (idNode != null && !idNode.asText().isEmpty()) {
                        result.add(idNode.asText());
                    }
                }
            }

            // 兜底：Ollama 旧版 /api/tags 格式 { "models": [{ "name": "...", ... }] }
            if (result.isEmpty()) {
                JsonNode modelsNode = root.get("models");
                if (modelsNode != null && modelsNode.isArray()) {
                    for (JsonNode model : modelsNode) {
                        JsonNode nameNode = model.get("name");
                        if (nameNode != null && !nameNode.asText().isEmpty()) {
                            result.add(nameNode.asText());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析模型列表 JSON 失败", e);
        }
        return result;
    }
}
