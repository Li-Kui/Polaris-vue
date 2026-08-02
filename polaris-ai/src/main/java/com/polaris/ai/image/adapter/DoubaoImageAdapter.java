package com.polaris.ai.image.adapter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.image.ImageGenRequest;
import com.polaris.ai.image.ImageProviderAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 火山引擎 Ark（豆包 Doubao/Seedream）绘图适配器
 * 支持文生图、图生图、多图融合、组图生成
 *
 * @author polaris
 */
@Slf4j
@Component
public class DoubaoImageAdapter implements ImageProviderAdapter {

    private static final String ARK_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";

    @Override
    public boolean supports(String provider) {
        return "ark".equals(provider);
    }

    @Override
    public String generate(ImageGenRequest request) throws Exception {
        AiModelConfig config = request.getConfig();
        String prompt = request.getPrompt();
        String taskId = request.getTaskId();

        String apiKey = config.getApiKey();
        String modelName = config.getModelName() != null && !config.getModelName().isEmpty()
                ? config.getModelName() : "doubao-seedream-5-0-260128";

        // 解析 baseUrl（兼容用户可能填入完整路径的情况）
        String baseUrl = ARK_BASE_URL;
        if (config.getBaseUrl() != null && !config.getBaseUrl().trim().isEmpty()) {
            String cfgUrl = config.getBaseUrl().trim();

            if (config.isRelay()) {
                // ── 中转站：baseUrl 直接使用，不做 Ark 特有路径拼接 ──
                while (cfgUrl.endsWith("/")) cfgUrl = cfgUrl.substring(0, cfgUrl.length() - 1);
                baseUrl = cfgUrl;
            } else {
                // ── 直连 Ark：保持现有解析逻辑 ──
                cfgUrl = cfgUrl.replace("/images/generations", "");
                cfgUrl = cfgUrl.replace("/v3/", "");
                cfgUrl = cfgUrl.replace("/v3", "");
                while (cfgUrl.endsWith("/")) {
                    cfgUrl = cfgUrl.substring(0, cfgUrl.length() - 1);
                }
                if (cfgUrl.endsWith("/api")) {
                    baseUrl = cfgUrl + "/v3";
                } else if (cfgUrl.contains("/api/")) {
                    int idx = cfgUrl.indexOf("/api/");
                    baseUrl = cfgUrl.substring(0, idx) + "/api/v3";
                } else {
                    baseUrl = cfgUrl + "/api/v3";
                }
            }
        }

        // 尺寸映射：系统 1024x1024 → Doubao "2K"；2048x2048 → "4K"
        String size;
        if (config.isRelay()) {
            // 中转站模式：使用标准像素格式，中转站内部处理转换
            size = request.getSize() != null && !request.getSize().isEmpty()
                    ? request.getSize() : "1024x1024";
        } else {
            // 直连模式：映射为 Ark 特有格式（2K/4K）
            size = mapSize(request.getSize(), config.getDefaultImageSize());
        }

        String createUrl = baseUrl + "/images/generations";

        JSONObject body = new JSONObject();
        body.put("model", modelName);
        body.put("prompt", prompt);
        body.put("size", size);
        body.put("response_format", "url");
        body.put("watermark", false);

        String mode = request.getGenerationMode() != null ? request.getGenerationMode() : "text_to_image";

        switch (mode) {
            case "multi_image": {
                java.util.List<String> sources = request.getSourceImageUrls();
                if (sources != null && !sources.isEmpty()) {
                    if (sources.size() == 1) {
                        body.put("image", sources.get(0));
                    } else {
                        body.put("image", new JSONArray(sources));
                    }
                } else if (request.getRefImageUrl() != null && !request.getRefImageUrl().isEmpty()) {
                    body.put("image", request.getRefImageUrl());
                }
                break;
            }
            case "image_to_image":
            case "image_edit": {
                // Seedream 4.0 原生支持多图输入：把所有源图作为数组传入，模型可跨图迁移
                // （如"把图1的车改成图2的颜色"），颜色/风格信息直接由模型从像素读取，不再依赖文字转述
                java.util.List<String> sources = request.getSourceImageUrls();
                if (sources != null && !sources.isEmpty()) {
                    if (sources.size() == 1) {
                        body.put("image", sources.get(0));
                    } else {
                        body.put("image", new JSONArray(sources));
                    }
                } else if (request.getRefImageUrl() != null && !request.getRefImageUrl().isEmpty()) {
                    body.put("image", request.getRefImageUrl());
                }
                // 多图编辑/融合场景必须显式关闭序列生成，避免误触发批量组图
                body.put("sequential_image_generation", "disabled");
                if ("image_edit".equals(mode)) {
                    // 前端已做像素级精确替换时，把 prompt 改写为"保持原图，仅精修"
                    boolean pixelExact = request.getExtra() != null
                            && Boolean.TRUE.equals(request.getExtra().get("pixelExact"));
                    if (pixelExact) {
                        body.put("prompt", "保持原图完全不变，仅提升画质与边缘平滑度");
                        log.info(">>> [DoubaoImageAdapter] image_edit + pixelExact → 低强度精修, taskId={}", taskId);
                    } else {
                        log.info(">>> [DoubaoImageAdapter] image_edit 多图输入, 源图 {} 张, taskId={}",
                                sources != null ? sources.size() : 0, taskId);
                    }
                }
                break;
            }
            case "text_to_image": {
                break;
            }
            default: {
                // 其他编辑模式降级为图生图
                if (request.getRefImageUrl() != null && !request.getRefImageUrl().isEmpty()) {
                    body.put("image", request.getRefImageUrl());
                }
                log.info(">>> [DoubaoImageAdapter] 模式 {} 不被 Doubao 原生支持，降级处理, taskId={}", mode, taskId);
                break;
            }
        }

        // 支持组图生成
        if (request.getN() > 1) {
            body.put("sequential_image_generation", "auto");
            JSONObject options = new JSONObject();
            options.put("max_images", Math.min(request.getN(), 15));
            body.put("sequential_image_generation_options", options);
        }

        log.info(">>> [DoubaoImageAdapter] 创建任务, taskId={}, mode={}, model={}, size={}, url={}",
                taskId, mode, modelName, size, createUrl);

        HttpURLConnection conn = (HttpURLConnection) new URL(createUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toJSONString().getBytes(StandardCharsets.UTF_8));
        }

        int httpCode = conn.getResponseCode();
        String respBody = readResponseStream(httpCode >= 400 ? conn.getErrorStream() : conn.getInputStream());

        if (httpCode != 200) {
            throw new RuntimeException("Ark 创建图像任务失败, HTTP " + httpCode + ": " + respBody);
        }

        JSONObject createResp = JSON.parseObject(respBody);
        JSONArray data = createResp.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("Ark 未返回有效图片数据: " + respBody);
        }

        String imageUrl = data.getJSONObject(0).getString("url");
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new RuntimeException("Ark 响应中未找到图片 URL: " + respBody);
        }

        log.info(">>> [DoubaoImageAdapter] 图像生成成功, taskId={}, url={}", taskId, imageUrl);
        return imageUrl;
    }

    private String mapSize(String systemSize, String defaultSize) {
        if (systemSize != null && !systemSize.isEmpty()) {
            return convertToDoubaoSize(systemSize);
        }
        if (defaultSize != null && !defaultSize.isEmpty()) {
            return convertToDoubaoSize(defaultSize);
        }
        return "2K";
    }

    private String convertToDoubaoSize(String size) {
        if (size == null || size.isEmpty()) {
            return "2K";
        }
        String lower = size.toLowerCase().trim();
        if ("2k".equals(lower) || "3k".equals(lower) || "4k".equals(lower)) {
            return lower.toUpperCase();
        }
        if ("1k".equals(lower)) {
            return "2K";
        }
        if (lower.contains("x")) {
            try {
                String[] parts = lower.split("x");
                int width = Integer.parseInt(parts[0].trim());
                int height = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : width;
                int maxDim = Math.max(width, height);
                if (maxDim <= 2560) {
                    return "2K";
                } else if (maxDim <= 4096) {
                    return "4K";
                } else {
                    return width + "x" + height;
                }
            } catch (NumberFormatException e) {
                return "2K";
            }
        }
        return "2K";
    }

    private String readResponseStream(java.io.InputStream is) throws Exception {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
