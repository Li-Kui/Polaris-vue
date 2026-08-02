package com.polaris.ai.image.adapter;

import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.image.ImageGenRequest;
import com.polaris.ai.image.ImageProviderAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容文生图适配器（同时作为未知厂商的兜底适配器）
 *
 * @author polaris
 */
@Slf4j
@Component
public class OpenAiImageAdapter implements ImageProviderAdapter {

    @Override
    public boolean supports(String provider) {
        return "openai".equals(provider);
    }

    @Override
    public boolean isFallback() {
        return true;
    }

    @Override
    public String generate(ImageGenRequest request) throws Exception {
        AiModelConfig config = request.getConfig();
        String mode = request.getGenerationMode() != null ? request.getGenerationMode() : "text_to_image";

        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl().trim() : "";
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        String url = baseUrl + "/images/generations";

        String apiKey = config.getApiKey();
        String modelName = config.getModelName();
        if (modelName == null || modelName.isEmpty()) {
            throw new IllegalArgumentException("绘图模型名称不能为空，请在模型配置中填写 modelName");
        }

        com.alibaba.fastjson2.JSONObject body = new com.alibaba.fastjson2.JSONObject();
        body.put("model", modelName);
        body.put("prompt", request.getPrompt());
        body.put("size", request.getSize() != null ? request.getSize() : "1024x1024");
        body.put("n", request.getN() > 0 ? request.getN() : 1);
        body.put("response_format", "url");

        switch (mode) {
            case "text_to_image":
                // 纯文生图，无需额外字段
                break;
            case "image_edit":
            case "image_to_image":
                injectImage(body, request);
                break;
            case "inpainting":
                injectImage(body, request);
                if (request.getMaskImageUrl() != null && !request.getMaskImageUrl().isEmpty()) {
                    body.put("mask", request.getMaskImageUrl());
                }
                break;
            case "multi_image":
                java.util.List<String> sources = request.getSourceImageUrls();
                if (sources != null && !sources.isEmpty()) {
                    body.put("image", sources.size() == 1 ? sources.get(0) : new com.alibaba.fastjson2.JSONArray(sources));
                }
                break;
            default:
                // 其他高级能力尝试发送，让中转站/厂商决定
                injectImage(body, request);
                break;
        }

        log.info(">>> [OpenAiImageAdapter] taskId={}, mode={}, model={}, url={}",
                request.getTaskId(), mode, modelName, url);

        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(body.toJSONString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        int httpCode = conn.getResponseCode();
        String respBody = readStream(
                httpCode >= 400 ? conn.getErrorStream() : conn.getInputStream());

        if (httpCode != 200) {
            throw new RuntimeException("OpenAI 兼容绘图请求失败, HTTP " + httpCode + ": " + respBody);
        }

        com.alibaba.fastjson2.JSONObject resp = com.alibaba.fastjson2.JSON.parseObject(respBody);
        com.alibaba.fastjson2.JSONArray data = resp.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("未返回有效图片数据: " + respBody);
        }
        String imageUrl = data.getJSONObject(0).getString("url");
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new RuntimeException("响应中未找到图片 URL: " + respBody);
        }

        log.info(">>> [OpenAiImageAdapter] 图像生成成功, taskId={}, url={}", request.getTaskId(), imageUrl);
        return imageUrl;
    }

    private void injectImage(com.alibaba.fastjson2.JSONObject body, ImageGenRequest request) {
        java.util.List<String> sources = request.getSourceImageUrls();
        if (sources != null && !sources.isEmpty()) {
            body.put("image", sources.size() == 1 ? sources.get(0) : new com.alibaba.fastjson2.JSONArray(sources));
        } else if (request.getRefImageUrl() != null && !request.getRefImageUrl().isEmpty()) {
            body.put("image", request.getRefImageUrl());
        }
    }

    private String readStream(java.io.InputStream is) throws Exception {
        if (is == null) return "";
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
