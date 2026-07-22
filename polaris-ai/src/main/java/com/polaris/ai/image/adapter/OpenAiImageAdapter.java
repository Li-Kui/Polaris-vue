package com.polaris.ai.image.adapter;

import com.polaris.ai.image.ImageGenRequest;
import com.polaris.ai.image.ImageProviderAdapter;
import com.polaris.ai.pivot.AiModelFactory;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容文生图适配器（同时作为未知厂商的兜底适配器）
 *
 * @author polaris
 */
@Slf4j
@Component
public class OpenAiImageAdapter implements ImageProviderAdapter {

    @Autowired
    private AiModelFactory modelFactory;

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
        String mode = request.getGenerationMode() != null ? request.getGenerationMode() : "text_to_image";
        // OpenAI 兼容接口（langchain4j ImageModel）目前仅支持文生图；
        // 其余编辑类能力若走到兜底适配器，明确报错而非静默降级出错图（与 DashScope 适配器保持一致）
        if (!"text_to_image".equals(mode)) {
            throw new UnsupportedOperationException(
                    "当前厂商[" + (request.getConfig() != null ? request.getConfig().getProvider() : "unknown")
                            + "]的适配器暂不支持能力[" + mode + "]，请改用支持该能力的模型（如通义万相）");
        }
        log.info(">>> [OpenAiImageAdapter] 使用 OpenAI 兼容接口生成图像, taskId: {}", request.getTaskId());
        ImageModel imageModel = modelFactory.getImageModel(request.getConfig());
        if (imageModel == null) {
            throw new RuntimeException("图像模型构建失败，请检查配置。");
        }
        Response<Image> response = imageModel.generate(request.getPrompt());
        if (response == null || response.content() == null || response.content().url() == null) {
            throw new RuntimeException("API 接口响应异常，未能成功生成图像");
        }
        return response.content().url().toString();
    }
}
