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
        params.put("n", cmd.getN() > 0 ? cmd.getN() : 1);
        params.put("negativePrompt", cmd.getNegativePrompt());
        task.setImageParams(JSON.toJSONString(params));
        task.setCreateTime(new Date());
        imageTaskService.createTask(task);

        // 4. 异步派发，具体厂商由 dispatcher 选择
        final AiModelConfig finalConfig = config;
        final String finalSize = size;
        final String firstRef = sources.isEmpty() ? null : sources.get(0);
        threadPoolTaskExecutor.execute(() -> {
            long start = System.currentTimeMillis();
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
                req.setN(cmd.getN() > 0 ? cmd.getN() : 1);
                req.setTaskId(taskId);
                req.setExpandParams(cmd.getExpandParams());
                req.setUpscaleFactor(cmd.getUpscaleFactor());
                req.setExtra(cmd.getExtra());

                String imageUrl = imageProviderDispatcher.generate(req);

                // 转存到本地，规避厂商临时 URL 过期；失败则回退厂商原始 URL
                String storedUrl = imageStorageHelper.transferToLocal(imageUrl);
                if (storedUrl != null && !storedUrl.isEmpty()) {
                    imageUrl = storedUrl;
                } else {
                    log.warn(">>> [ImageGenerationService] 图片转存失败，回退厂商原始 URL, taskId: {}", taskId);
                }

                imageTaskService.markSuccess(taskId, imageUrl, System.currentTimeMillis() - start);
                log.info(">>> [ImageGenerationService] 绘图任务生成成功, taskId: {}, url: {}", taskId, imageUrl);
            } catch (Exception e) {
                log.error(">>> [ImageGenerationService] 绘图异步任务失败, taskId: {}", taskId, e);
                imageTaskService.markFail(taskId, e.getMessage());
            }
        });

        return task;
    }

    @Override
    public java.util.List<String> listSupportedModes() {
        return new java.util.ArrayList<>(capabilityRouter.supportedModes());
    }
}
