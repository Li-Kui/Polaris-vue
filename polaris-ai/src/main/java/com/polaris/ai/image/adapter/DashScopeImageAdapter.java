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
 * DashScope（阿里通义万相）原生文生图适配器：异步创建任务 + 轮询结果
 *
 * @author polaris
 */
@Slf4j
@Component
public class DashScopeImageAdapter implements ImageProviderAdapter {

    @Override
    public boolean supports(String provider) {
        return "dashscope".equals(provider);
    }

    @Override
    public String generate(ImageGenRequest request) throws Exception {
        // ── 中转站模式：使用标准 OpenAI 兼容端点 ──
        if (request.getConfig().isRelay()) {
            return generateViaRelay(request);
        }
        return generateViaNative(request);
    }

    private String generateViaNative(ImageGenRequest request) throws Exception {
        AiModelConfig config = request.getConfig();
        String prompt = request.getPrompt();
        String taskId = request.getTaskId();
        String refImageUrl = request.getRefImageUrl();

        String apiKey = config.getApiKey();
        String modelName = config.getModelName() != null && !config.getModelName().isEmpty()
                ? config.getModelName() : "wanx-v1";

        // 解析 DashScope 基础域名（兼容用户可能填入 OpenAI 兼容路径的情况）
        String baseUrl = "https://dashscope.aliyuncs.com";
        if (config.getBaseUrl() != null && !config.getBaseUrl().trim().isEmpty()) {
            String cfgUrl = config.getBaseUrl().trim();
            int idx = cfgUrl.indexOf("/compatible-mode");
            if (idx > 0) cfgUrl = cfgUrl.substring(0, idx);
            idx = cfgUrl.indexOf("/v1");
            if (idx > 0) cfgUrl = cfgUrl.substring(0, idx);
            while (cfgUrl.endsWith("/")) cfgUrl = cfgUrl.substring(0, cfgUrl.length() - 1);
            baseUrl = cfgUrl;
        }

        // DashScope 尺寸记法用 *，将统一的 1024x1024 转换（仅文生图/图生图使用）
        String size = request.getSize() != null && !request.getSize().isEmpty()
                ? request.getSize().replace("x", "*") : "1024*1024";

        String mode = request.getGenerationMode() != null ? request.getGenerationMode() : "text_to_image";

        // 能力分流：wan2.7 系列走同步多模态接口（原生支持多图输入，如"把图1的车改成图2的颜色"）
        // 仅对编辑/图生图/文生图类启用；局部重绘、扩图、超分等特有能力仍走 wanx2.1 异步路径
        boolean isWan27 = modelName != null && modelName.startsWith("wan2.7");
        boolean multimodalCapable = "image_edit".equals(mode) || "image_to_image".equals(mode)
                || "multi_image".equals(mode) || "background_replacement".equals(mode)
                || "text_to_image".equals(mode);
        if (isWan27 && multimodalCapable) {
            return generateByMultimodal(request, baseUrl, apiKey, modelName, size);
        }

        // 编辑类模型名：用户未显式配置 imageedit 模型时默认 wanx2.1-imageedit
        String editModel = (modelName != null && modelName.contains("imageedit")) ? modelName : "wanx2.1-imageedit";

        // ======== 步骤 1: 按能力组装并创建异步任务 ========
        String createUrl;
        JSONObject body = new JSONObject();
        JSONObject input = new JSONObject();
        JSONObject params = new JSONObject();
        params.put("n", request.getN() > 0 ? request.getN() : 1);
        if (request.getNegativePrompt() != null && !request.getNegativePrompt().isEmpty()) {
            params.put("negative_prompt", request.getNegativePrompt());
        }

        switch (mode) {
            case "image_edit": {
                // 指令改图：全图指令编辑，无需遮罩
                createUrl = baseUrl + "/api/v1/services/aigc/image2image/image-synthesis";
                body.put("model", editModel);
                input.put("function", "description_edit");
                input.put("base_image_url", refImageUrl);

                // 前端已做像素级精确替换时，仅做低强度精修（边缘平滑/画质提升），不再让模型自由发挥重画
                boolean pixelExact = request.getExtra() != null
                        && Boolean.TRUE.equals(request.getExtra().get("pixelExact"));
                if (pixelExact) {
                    input.put("prompt", "保持原图完全不变，仅提升画质与边缘平滑度");
                    params.put("strength", 0.15f);
                } else {
                    // 强约束：保持构图/形状/位置不变，仅改变颜色，避免模型自由发挥重画整图
                    input.put("prompt", buildColorEditPrompt(prompt));
                    params.put("strength", 0.35f);
                }
                break;
            }
            case "inpainting": {
                // 局部重绘：带遮罩的指令编辑，仅重绘遮罩白色区域
                createUrl = baseUrl + "/api/v1/services/aigc/image2image/image-synthesis";
                body.put("model", editModel);
                input.put("function", "description_edit_with_mask");
                input.put("prompt", prompt);
                input.put("base_image_url", refImageUrl);
                input.put("mask_image_url", request.getMaskImageUrl());
                break;
            }
            case "object_removal": {
                // 去物体/去水印：使用 remove_watermark function
                createUrl = baseUrl + "/api/v1/services/aigc/image2image/image-synthesis";
                body.put("model", editModel);
                input.put("function", "remove_watermark");
                input.put("prompt", prompt != null && !prompt.isEmpty() ? prompt : "去除图中不需要的物体");
                input.put("base_image_url", refImageUrl);
                break;
            }
            case "outpainting": {
                // 智能扩图：向任意方向扩展画布
                createUrl = baseUrl + "/api/v1/services/aigc/image2image/image-synthesis";
                body.put("model", editModel);
                input.put("function", "expand");
                input.put("prompt", prompt);
                input.put("base_image_url", refImageUrl);
                parseExpandScales(request, params);
                break;
            }
            case "background_replacement": {
                // 背景替换：指令编辑（全图改背景，无需遮罩）
                createUrl = baseUrl + "/api/v1/services/aigc/image2image/image-synthesis";
                body.put("model", editModel);
                input.put("function", "description_edit");
                input.put("prompt", prompt);
                input.put("base_image_url", refImageUrl);
                break;
            }
            case "upscale": {
                // 图像超分：放大并增强细节
                createUrl = baseUrl + "/api/v1/services/aigc/image2image/image-synthesis";
                body.put("model", editModel);
                input.put("function", "super_resolution");
                input.put("prompt", prompt != null && !prompt.isEmpty() ? prompt : "提升图像分辨率");
                input.put("base_image_url", refImageUrl);
                parseUpscaleFactor(request, params);
                break;
            }
            case "restoration": {
                // 图像修复/增强：使用全局风格化（低强度尽量保持原图）
                createUrl = baseUrl + "/api/v1/services/aigc/image2image/image-synthesis";
                body.put("model", editModel);
                input.put("function", "stylization_all");
                input.put("prompt", prompt != null && !prompt.isEmpty() ? prompt : "保持原貌，提升画质");
                input.put("base_image_url", refImageUrl);
                params.put("strength", 0.1f);
                break;
            }
            case "multi_image": {
                // 多图合成：多张参考图合并生成新图
                createUrl = baseUrl + "/api/v1/services/aigc/text2image/image-synthesis";
                body.put("model", modelName);
                input.put("prompt", prompt);
                java.util.List<String> sources = request.getSourceImageUrls();
                if (sources != null && !sources.isEmpty()) {
                    if (sources.size() == 1) {
                        input.put("ref_image", sources.get(0));
                    } else {
                        input.put("ref_image", new com.alibaba.fastjson2.JSONArray(sources));
                    }
                    params.put("ref_mode", "repaint");
                }
                params.put("size", size);
                break;
            }
            case "colorization":
            case "doodle":
                // colorization（上色）、doodle（线稿生图）暂用描述编辑兜底
                createUrl = baseUrl + "/api/v1/services/aigc/image2image/image-synthesis";
                body.put("model", editModel);
                input.put("function", "description_edit");
                input.put("prompt", prompt);
                input.put("base_image_url", refImageUrl);
                break;
            case "image_to_image": {
                // 以图生图：在原图基础上按 prompt 进行编辑，保留原图主体
                createUrl = baseUrl + "/api/v1/services/aigc/image2image/image-synthesis";
                body.put("model", editModel);
                input.put("function", "description_edit");
                input.put("prompt", prompt);
                input.put("base_image_url", refImageUrl);
                params.put("strength", 0.5f);
                break;
            }
            case "text_to_image":
            default: {
                createUrl = baseUrl + "/api/v1/services/aigc/text2image/image-synthesis";
                body.put("model", modelName);
                input.put("prompt", prompt);
                if (refImageUrl != null && !refImageUrl.isEmpty()) {
                    input.put("ref_image", refImageUrl);
                    params.put("ref_mode", "repaint");
                    params.put("ref_strength", 0.7f);
                }
                params.put("size", size);
                break;
            }
        }
        body.put("input", input);
        body.put("parameters", params);

        log.info(">>> [DashScopeImageAdapter] 创建任务, taskId={}, mode={}, model={}, url={}",
                taskId, mode, body.getString("model"), createUrl);

        HttpURLConnection conn = (HttpURLConnection) new URL(createUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-DashScope-Async", "enable");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toJSONString().getBytes(StandardCharsets.UTF_8));
        }

        int httpCode = conn.getResponseCode();
        String respBody = readResponseStream(httpCode >= 400 ? conn.getErrorStream() : conn.getInputStream());

        if (httpCode != 200) {
            throw new RuntimeException("DashScope 创建图像任务失败, HTTP " + httpCode + ": " + respBody);
        }

        JSONObject createResp = JSON.parseObject(respBody);
        JSONObject output = createResp.getJSONObject("output");
        if (output == null || output.getString("task_id") == null) {
            throw new RuntimeException("DashScope 未返回有效 task_id: " + respBody);
        }
        String dashTaskId = output.getString("task_id");
        log.info(">>> [DashScopeImageAdapter] 任务已创建, taskId={}, dashTaskId={}", taskId, dashTaskId);

        // ======== 步骤 2: 轮询任务状态（每 3 秒，最长 6 分钟） ========
        String queryUrl = baseUrl + "/api/v1/tasks/" + dashTaskId;

        for (int i = 0; i < 120; i++) {
            Thread.sleep(3000);

            HttpURLConnection qConn = (HttpURLConnection) new URL(queryUrl).openConnection();
            qConn.setRequestMethod("GET");
            qConn.setRequestProperty("Authorization", "Bearer " + apiKey);
            qConn.setConnectTimeout(10000);
            qConn.setReadTimeout(30000);

            int qCode = qConn.getResponseCode();
            String qBody = readResponseStream(qCode >= 400 ? qConn.getErrorStream() : qConn.getInputStream());
            JSONObject qResp = JSON.parseObject(qBody);
            JSONObject qOutput = qResp.getJSONObject("output");
            String taskStatus = qOutput != null ? qOutput.getString("task_status") : "UNKNOWN";

            if ("SUCCEEDED".equals(taskStatus)) {
                String imageUrl = extractImageUrl(qOutput);
                if (imageUrl != null) {
                    log.info(">>> [DashScopeImageAdapter] 图像生成成功, taskId={}, dashTaskId={}", taskId, dashTaskId);
                    return imageUrl;
                }
                throw new RuntimeException("DashScope 任务成功但未返回图片 URL");
            } else if ("FAILED".equals(taskStatus)) {
                String errCode = qOutput.getString("code");
                String errMsg = qOutput.getString("message");
                throw new RuntimeException("图像生成失败 [" + errCode + "]: " + (errMsg != null ? errMsg : qBody));
            }
            // PENDING / RUNNING: 继续轮询
        }

        throw new RuntimeException("图像生成超时，未在 6 分钟内完成");
    }

    /**
     * wan2.7 系列同步多模态生成：原生支持多图输入。
     * 请求体结构：input.messages[].content = [{image:url1},{image:url2},...,{text:prompt}]
     * 响应同步返回：output.choices[0].message.content[0].image
     */
    private String generateByMultimodal(ImageGenRequest request, String baseUrl, String apiKey,
                                        String modelName, String size) throws Exception {
        String createUrl = baseUrl + "/api/v1/services/aigc/multimodal-generation/generation";
        String taskId = request.getTaskId();

        // 组装 content：先按顺序放图片（图1、图2……），最后放文本指令
        JSONArray content = new JSONArray();
        java.util.List<String> sources = request.getSourceImageUrls();
        int imageCount = 0;
        if (sources != null && !sources.isEmpty()) {
            for (String url : sources) {
                if (url != null && !url.trim().isEmpty()) {
                    JSONObject img = new JSONObject();
                    img.put("image", url.trim());
                    content.add(img);
                    imageCount++;
                }
            }
        }
        JSONObject text = new JSONObject();
        text.put("text", request.getPrompt());
        content.add(text);

        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", content);

        JSONObject input = new JSONObject();
        input.put("messages", new JSONArray().fluentAdd(message));

        JSONObject params = new JSONObject();
        params.put("n", request.getN() > 0 ? request.getN() : 1);
        params.put("size", size);
        params.put("watermark", false);
        if (request.getNegativePrompt() != null && !request.getNegativePrompt().isEmpty()) {
            params.put("negative_prompt", request.getNegativePrompt());
        }

        JSONObject body = new JSONObject();
        body.put("model", modelName);
        body.put("input", input);
        body.put("parameters", params);

        log.info(">>> [DashScopeImageAdapter] wan2.7 同步多模态生成, taskId={}, model={}, 输入图 {} 张, url={}",
                taskId, modelName, imageCount, createUrl);

        HttpURLConnection conn = (HttpURLConnection) new URL(createUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        // 注意：多模态同步接口不加 X-DashScope-Async
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(180000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toJSONString().getBytes(StandardCharsets.UTF_8));
        }

        int httpCode = conn.getResponseCode();
        String respBody = readResponseStream(httpCode >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (httpCode != 200) {
            throw new RuntimeException("DashScope 多模态生成失败, HTTP " + httpCode + ": " + respBody);
        }

        JSONObject resp = JSON.parseObject(respBody);
        JSONObject output = resp.getJSONObject("output");
        if (output == null) {
            throw new RuntimeException("DashScope 多模态未返回 output: " + respBody);
        }
        String imageUrl = extractImageUrl(output);
        if (imageUrl == null) {
            throw new RuntimeException("DashScope 多模态未返回图片 URL: " + respBody);
        }
        log.info(">>> [DashScopeImageAdapter] wan2.7 生成成功, taskId={}", taskId);
        return imageUrl;
    }

    /** 读取 HTTP 响应流并返回字符串 */
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

    /**
     * 兼容新旧两种响应格式提取图片 URL。
     * 旧 endpoint（image2image/text2image）：output.results[0].url
     * 新 2.7 endpoint（multimodal-generation）：output.choices[0].message.content[0].image
     */
    private String extractImageUrl(JSONObject output) {
        // 旧格式：results[0].url
        JSONArray results = output.getJSONArray("results");
        if (results != null && !results.isEmpty()) {
            JSONObject first = results.getJSONObject(0);
            if (first != null && first.containsKey("url")) {
                return first.getString("url");
            }
        }
        // 新 2.7 格式：choices[0].message.content[0].image
        JSONArray choices = output.getJSONArray("choices");
        if (choices != null && !choices.isEmpty()) {
            JSONObject msg = choices.getJSONObject(0).getJSONObject("message");
            if (msg != null) {
                JSONArray content = msg.getJSONArray("content");
                if (content != null && !content.isEmpty()) {
                    for (int i = 0; i < content.size(); i++) {
                        JSONObject c = content.getJSONObject(i);
                        if ("image".equals(c.getString("type")) && c.containsKey("image")) {
                            return c.getString("image");
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 为"颜色改图"指令追加结构化约束，避免 description_edit 自由发挥。
     * 自动判断 prompt 中是否涉及颜色，是则强化约束。
     */
    private String buildColorEditPrompt(String originalPrompt) {
        if (originalPrompt == null || originalPrompt.trim().isEmpty()) {
            return originalPrompt;
        }
        // 匹配：颜色/改成/红/紫/第N种颜色 等颜色相关关键词
        String colorRegex = "(颜色|改成|换成|变为|调成|换为|染成|染色|色系|调色|红|橙|黄|绿|青|蓝|紫|粉|黑|白|灰|第[一二三四五六七八九十1-9]种颜色)";
        boolean isColorEdit = originalPrompt.matches(".*" + colorRegex + ".*");
        if (!isColorEdit) {
            return originalPrompt;
        }
        // 追加强约束：保持构图/位置/形状不变，仅改变颜色
        return originalPrompt + "。严格保持原图所有物体的形状、大小、位置、数量、布局完全不变，"
                + "只修改指定颜色，不要新增、删除或移动任何元素，不要改变背景。";
    }

    /** 解析扩图参数，支持 top/bottom/left/right_scale，默认等比扩 1.5x */
    private void parseExpandScales(ImageGenRequest request, JSONObject params) {
        try {
            String ep = request.getExpandParams();
            JSONObject p = ep != null ? JSON.parseObject(ep) : null;
            params.put("top_scale",    getFloat(p, "top_scale",    1.5f));
            params.put("bottom_scale", getFloat(p, "bottom_scale", 1.5f));
            params.put("left_scale",   getFloat(p, "left_scale",   1.5f));
            params.put("right_scale",  getFloat(p, "right_scale",  1.5f));
        } catch (Exception e) {
            params.put("top_scale", 1.5f);
            params.put("bottom_scale", 1.5f);
            params.put("left_scale", 1.5f);
            params.put("right_scale", 1.5f);
        }
    }

    /** 解析超分参数 upscale_factor，默认 2 */
    private void parseUpscaleFactor(ImageGenRequest request, JSONObject params) {
        try {
            Integer factor = request.getUpscaleFactor();
            int f = (factor != null) ? Math.min(Math.max(factor, 1), 4) : 2;
            params.put("upscale_factor", f);
        } catch (Exception e) {
            params.put("upscale_factor", 2);
        }
    }

    private float getFloat(JSONObject obj, String key, float def) {
        if (obj == null || !obj.containsKey(key)) return def;
        return (float) obj.getDoubleValue(key);
    }

    /**
     * 中转站模式：标准 /images/generations 端点 + 万相参数优化。
     * 中转站内部负责调用 DashScope 原生异步 API 并同步返回结果。
     */
    private String generateViaRelay(ImageGenRequest request) throws Exception {
        AiModelConfig config = request.getConfig();
        String taskId = request.getTaskId();
        String apiKey = config.getApiKey();
        String modelName = config.getModelName() != null && !config.getModelName().isEmpty()
                ? config.getModelName() : "wanx-v1";

        // 中转站 baseUrl 直接使用，不做 DashScope 特有路径裁剪
        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl().trim() : "";
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        String url = baseUrl + "/images/generations";

        String size = request.getSize() != null && !request.getSize().isEmpty()
                ? request.getSize() : "1024x1024";

        JSONObject body = new JSONObject();
        body.put("model", modelName);
        body.put("response_format", "url");
        body.put("n", request.getN() > 0 ? request.getN() : 1);
        body.put("size", size);

        String mode = request.getGenerationMode() != null ? request.getGenerationMode() : "text_to_image";

        switch (mode) {
            case "text_to_image":
                body.put("prompt", request.getPrompt());
                if (request.getRefImageUrl() != null && !request.getRefImageUrl().isEmpty()) {
                    body.put("image", request.getRefImageUrl());
                }
                break;
            case "image_edit":
                boolean pixelExact = request.getExtra() != null
                        && Boolean.TRUE.equals(request.getExtra().get("pixelExact"));
                if (pixelExact) {
                    body.put("prompt", "保持原图完全不变，仅提升画质与边缘平滑度");
                } else {
                    body.put("prompt", request.getPrompt());
                }
                injectRelayImage(body, request);
                break;
            case "image_to_image":
                body.put("prompt", request.getPrompt());
                injectRelayImage(body, request);
                break;
            case "inpainting":
                body.put("prompt", request.getPrompt());
                injectRelayImage(body, request);
                if (request.getMaskImageUrl() != null && !request.getMaskImageUrl().isEmpty()) {
                    body.put("mask", request.getMaskImageUrl());
                }
                break;
            case "multi_image":
                body.put("prompt", request.getPrompt());
                java.util.List<String> sources = request.getSourceImageUrls();
                if (sources != null && !sources.isEmpty()) {
                    body.put("image", sources.size() == 1 ? sources.get(0) : new com.alibaba.fastjson2.JSONArray(sources));
                }
                break;
            default:
                body.put("prompt", request.getPrompt());
                injectRelayImage(body, request);
                break;
        }

        log.info(">>> [DashScopeImageAdapter] 中转站模式, taskId={}, mode={}, model={}, url={}",
                taskId, mode, modelName, url);

        // 发送请求
        HttpURLConnection conn = (HttpURLConnection) new java.net.URL(url).openConnection();
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
        String respBody = readResponseStream(
                httpCode >= 400 ? conn.getErrorStream() : conn.getInputStream());

        if (httpCode != 200) {
            throw new RuntimeException("中转站绘图请求失败, HTTP " + httpCode + ": " + respBody);
        }

        // 解析 OpenAI 标准响应格式
        JSONObject resp = JSON.parseObject(respBody);
        com.alibaba.fastjson2.JSONArray data = resp.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("中转站未返回有效图片数据: " + respBody);
        }
        String imageUrl = data.getJSONObject(0).getString("url");
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new RuntimeException("中转站响应中未找到图片 URL: " + respBody);
        }

        log.info(">>> [DashScopeImageAdapter] 中转站模式图像生成成功, taskId={}, url={}", taskId, imageUrl);
        return imageUrl;
    }

    private void injectRelayImage(JSONObject body, ImageGenRequest request) {
        java.util.List<String> sources = request.getSourceImageUrls();
        if (sources != null && !sources.isEmpty()) {
            body.put("image", sources.size() == 1 ? sources.get(0) : new com.alibaba.fastjson2.JSONArray(sources));
        } else if (request.getRefImageUrl() != null && !request.getRefImageUrl().isEmpty()) {
            body.put("image", request.getRefImageUrl());
        }
    }
}
