package com.polaris.ai.service.impl;

import com.alibaba.fastjson2.JSON;
import com.polaris.ai.domain.AiImageTask;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.enums.ImageGenerationMode;
import com.polaris.ai.image.ImageGenCommand;
import com.polaris.ai.image.ImageGenRequest;
import com.polaris.ai.image.ImageProviderDispatcher;
import com.polaris.ai.image.ImageStorageHelper;
import com.polaris.ai.pivot.ImageModelCapabilityRouter;
import com.polaris.ai.service.IAiImageTaskService;
import com.polaris.ai.service.IImageGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 绘图任务编排服务实现。
 *
 * @author polaris
 */
@Slf4j
@Service
public class ImageGenerationServiceImpl implements IImageGenerationService {

    @Autowired
    private ImageModelCapabilityRouter capabilityRouter;

    @Autowired
    private ImageProviderDispatcher imageProviderDispatcher;

    @Autowired
    private ImageStorageHelper imageStorageHelper;

    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Autowired
    private IAiImageTaskService imageTaskService;

    @Autowired
    private com.polaris.ai.image.ImageGenConcurrencyManager concurrencyManager;

    @Override
    public AiImageTask submit(ImageGenCommand cmd) {
        // 1. 校验能力
        ImageGenerationMode mode = ImageGenerationMode.fromCode(cmd.getGenerationMode());
        if (mode == null) {
            throw new IllegalArgumentException("不支持的生成能力: " + cmd.getGenerationMode());
        }
        final List<String> sources = cmd.getSourceImages() != null ? cmd.getSourceImages() : Collections.emptyList();
        if (mode.isNeedsSource() && sources.isEmpty()) {
            throw new IllegalArgumentException("能力[" + mode.getLabel() + "]需要至少一张源图");
        }
        if (mode.isNeedsMask() && (cmd.getMaskImage() == null || cmd.getMaskImage().trim().isEmpty())) {
            throw new IllegalArgumentException("能力[" + mode.getLabel() + "]需要遮罩图");
        }

        // 2. 能力路由选模型
        AiModelConfig config = capabilityRouter.route(mode.getCode());
        if (config == null || config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new IllegalStateException("后台未配置支持[" + mode.getLabel() + "]的图像生成服务，请联系管理员配置");
        }
        // fail-fast：路由可能回退默认模型，这里严格复核所选模型确实声明支持该能力，避免"静默降级出错图"
        if (!capabilityRouter.supports(config, mode.getCode())) {
            throw new IllegalStateException("后台未配置支持[" + mode.getLabel() + "]能力的图像模型，请在模型管理中为对应模型开启该能力");
        }

        // 防爆保护：单次最多允许生成 10 张图
        int targetN = cmd.getN() > 0 ? cmd.getN() : 1;
        if (targetN > 10) {
            log.warn(">>> [ImageGenerationService] 请求生成数量 {} 超过单次上线上限 10，自动截断为 10", targetN);
            targetN = 10;
        }
        cmd.setN(targetN);

        // 3. 落库任务
        String taskId = "img_" + UUID.randomUUID().toString().replaceAll("-", "");
        String size = (cmd.getSize() != null && !cmd.getSize().isEmpty()) ? cmd.getSize() : config.getDefaultImageSize();

        AiImageTask task = new AiImageTask();
        task.setTaskId(taskId);
        task.setPrompt(cmd.getPrompt());
        task.setStatus("0"); // 生成中
        task.setGenerationMode(mode.getCode());
        task.setModelConfigId(config.getId());
        task.setProvider(config.getProvider());
        if (!sources.isEmpty()) {
            task.setSourceImages(JSON.toJSONString(sources));
        }
        if (cmd.getMaskImage() != null && !cmd.getMaskImage().trim().isEmpty()) {
            task.setMaskImage(cmd.getMaskImage());
        }
        task.setConversationId(cmd.getConversationId());
        Map<String, Object> params = new HashMap<>();
        params.put("size", size);
        params.put("n", targetN);
        params.put("negativePrompt", cmd.getNegativePrompt());
        task.setImageParams(JSON.toJSONString(params));
        task.setCreateTime(new Date());
        imageTaskService.createTask(task);

        // 4. 异步派发，支持模型能力自适应与自动批次并发拆分
        final AiModelConfig finalConfig = config;
        final String finalSize = size;
        final String firstRef = sources.isEmpty() ? null : sources.get(0);
        final int finalN = targetN;

        threadPoolTaskExecutor.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                List<String> promptList = cmd.getPrompts();
                List<java.util.concurrent.CompletableFuture<List<String>>> batchFutures = new ArrayList<>();

                if (promptList != null && !promptList.isEmpty()) {
                    log.info(">>> [ImageGenerationService] 启用 Multi-Prompt 独立并发架构, 共有 {} 条独立视觉描述", promptList.size());
                    for (int i = 0; i < promptList.size(); i++) {
                        final String singlePrompt = promptList.get(i);
                        final int delayMs = i * 250; // 微错峰，平滑 API QPS，防止瞬间并发触发 HTTP 429
                        batchFutures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            if (delayMs > 0) {
                                try { Thread.sleep(delayMs); } catch (Exception ignored) {}
                            }
                            return generateListWithRetry(finalConfig, singlePrompt, mode.getCode(), finalSize, taskId, sources, cmd);
                        }, threadPoolTaskExecutor));
                    }
                } else {
                    // 回退单 Prompt 批次拆分引擎
                    int maxNativeN = getModelMaxNativeN(finalConfig);
                    List<Integer> batchSizes = splitIntoBatches(finalN, maxNativeN);
                    for (int subN : batchSizes) {
                        batchFutures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            try {
                                ImageGenRequest req = new ImageGenRequest();
                                req.setConfig(finalConfig);
                                req.setPrompt(cmd.getPrompt());
                                req.setNegativePrompt(cmd.getNegativePrompt());
                                req.setRefImageUrl(firstRef);
                                req.setSourceImageUrls(sources);
                                req.setMaskImageUrl(cmd.getMaskImage());
                                req.setGenerationMode(mode.getCode());
                                req.setSize(finalSize);
                                req.setN(subN);
                                req.setTaskId(taskId);
                                req.setExpandParams(cmd.getExpandParams());
                                req.setUpscaleFactor(cmd.getUpscaleFactor());
                                req.setExtra(cmd.getExtra());

                                return imageProviderDispatcher.generateList(req);
                            } catch (Exception e) {
                                log.error(">>> [ImageGenerationService] 子批次绘图异常, subN={}", subN, e);
                                return Collections.emptyList();
                            }
                        }, threadPoolTaskExecutor));
                    }
                }

                List<String> totalUrls = new ArrayList<>();
                for (java.util.concurrent.CompletableFuture<List<String>> future : batchFutures) {
                    List<String> resList = future.join();
                    if (resList != null && !resList.isEmpty()) {
                        totalUrls.addAll(resList);
                    }
                }

                if (totalUrls.isEmpty()) {
                    throw new RuntimeException("图片生成失败，所有子批次均未返回有效图片");
                }

                log.info(">>> [ImageGenerationService] 汇总成功，实际获取到 {} 张图片 (目标 {} 张)", totalUrls.size(), finalN);

                // 转存到本地，规避厂商临时 URL 过期；单图失败降级使用原始 URL
                String rawResult = totalUrls.size() == 1 ? totalUrls.get(0) : JSON.toJSONString(totalUrls);
                String storedUrl = imageStorageHelper.transferAllToLocal(rawResult, threadPoolTaskExecutor);
                if (storedUrl == null || storedUrl.isEmpty()) {
                    storedUrl = rawResult;
                }

                imageTaskService.markSuccess(taskId, storedUrl, System.currentTimeMillis() - start);
                log.info(">>> [ImageGenerationService] 绘图任务生成成功, taskId: {}, storedUrl: {}", taskId, storedUrl);
            } catch (Exception e) {
                log.error(">>> [ImageGenerationService] 绘图异步任务失败, taskId: {}", taskId, e);
                imageTaskService.markFail(taskId, e.getMessage());
            }
        });

        return task;
    }

    /**
     * 获取模型单次 API 支持的最大原生张数
     */
    private int getModelMaxNativeN(AiModelConfig config) {
        if (config == null) return 4;
        String provider = config.getProvider() != null ? config.getProvider().toLowerCase() : "";
        String modelName = config.getModelName() != null ? config.getModelName().toLowerCase() : "";

        if (modelName.contains("dall-e-3")) {
            return 1; // OpenAI DALL-E 3 强制要求 n=1
        }
        if ("doubao".equals(provider)) {
            return 15; // 豆包并发上限较宽
        }
        if ("dashscope".equals(provider)) {
            return 4; // 万相异步 API 上限 4
        }
        return 4; // 默认 4
    }

    /**
     * 将目标张数拆分为各子批次数量列表
     */
    private List<Integer> splitIntoBatches(int targetN, int maxNativeN) {
        List<Integer> batches = new ArrayList<>();
        int remain = targetN;
        int limit = maxNativeN > 0 ? maxNativeN : 4;
        while (remain > 0) {
            int current = Math.min(remain, limit);
            batches.add(current);
            remain -= current;
        }
        return batches;
    }

    /**
     * 针对单条提示词发起请求，具备动态信号量并发排队与 HTTP 429 限流自动退避重试能力
     */
    private List<String> generateListWithRetry(AiModelConfig config, String prompt, String modeCode, String size, String taskId, List<String> sources, ImageGenCommand cmd) {
        Long configId = config != null ? config.getId() : -1L;
        // 1. 获取在途并发许可（阻塞排队等待，保证发给厂商的在途渲染任务永远不超过并发限制，支持 AiModelConfig 自定义配置）
        concurrencyManager.acquire(config);
        try {
            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    ImageGenRequest req = new ImageGenRequest();
                    req.setConfig(config);
                    req.setPrompt(prompt);
                    req.setNegativePrompt(cmd.getNegativePrompt());
                    req.setRefImageUrl(sources.isEmpty() ? null : sources.get(0));
                    req.setSourceImageUrls(sources);
                    req.setMaskImageUrl(cmd.getMaskImage());
                    req.setGenerationMode(modeCode);
                    req.setSize(size);
                    req.setN(1);
                    req.setTaskId(taskId);
                    req.setExpandParams(cmd.getExpandParams());
                    req.setUpscaleFactor(cmd.getUpscaleFactor());
                    req.setExtra(cmd.getExtra());

                    return imageProviderDispatcher.generateList(req);
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    boolean isRateLimit = msg.contains("429") || msg.contains("RateQuota") || msg.contains("rate limit") || msg.contains("Throttling");
                    if (isRateLimit) {
                        // 触发 429 智能通知并发管理器下调上限
                        concurrencyManager.onRateLimitExceeded(configId);
                        if (attempt < maxRetries) {
                            long sleepMs = 4000L * attempt + (long) (Math.random() * 1000); // 匹配 GPU 生图渲染周期的长休眠
                            log.warn(">>> [ImageGenService] 触发厂商 RateLimit 429 限流, 尝试第 {} 次重试 (等待 {}ms), prompt: {}", attempt, sleepMs, prompt);
                            try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
                        } else {
                            log.error(">>> [ImageGenerationService] 子任务绘图失败 (attempt {}/{}), prompt: {}", attempt, maxRetries, prompt, e);
                            return Collections.emptyList();
                        }
                    } else {
                        log.error(">>> [ImageGenerationService] 子任务绘图失败 (attempt {}/{}), prompt: {}", attempt, maxRetries, prompt, e);
                        return Collections.emptyList();
                    }
                }
            }
            return Collections.emptyList();
        } finally {
            // 2. 在 finally 块中精准释放信号量许可，供后续排队任务执行
            concurrencyManager.release(configId);
        }
    }

    @Override
    public java.util.List<String> listSupportedModes() {
        return new java.util.ArrayList<>(capabilityRouter.supportedModes());
    }
}
