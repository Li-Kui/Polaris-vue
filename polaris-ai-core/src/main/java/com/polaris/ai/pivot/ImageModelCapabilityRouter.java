package com.polaris.ai.pivot;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.enums.ModelType;
import com.polaris.ai.service.IAiModelConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 图像能力路由器：按“生成能力(generationMode)”选出真正声明支持该能力的绘图模型。
 * 与 ImageProviderDispatcher（按 provider 选适配器）是不同维度，二者互补。
 *
 * @author polaris
 */
@Slf4j
@Component
public class ImageModelCapabilityRouter {

    @Autowired
    private AiModelFactory modelFactory;

    @Autowired
    private IAiModelConfigService modelConfigService;

    /**
     * 按能力路由选择绘图模型
     *
     * @param generationMode 生成能力 code（text_to_image / image_to_image / inpainting ...）
     * @return 支持该能力的模型配置；无任何支持模型时回退默认 IMAGE 模型（可能为 null）
     */
    public AiModelConfig route(String generationMode) {
        AiModelConfig defaultImage = modelFactory.getDefaultModelConfig(ModelType.IMAGE);

        // 默认模型即支持 → 直接用，兼顾数据权限（默认模型本身已按权限筛选）
        if (defaultImage != null && supports(defaultImage, generationMode)) {
            return defaultImage;
        }

        // 默认模型不支持 → 扫描所有启用的 IMAGE 模型，挑第一个声明支持的
        List<AiModelConfig> candidates = modelConfigService.list(
                new LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getModelType, ModelType.IMAGE.name())
                        .eq(AiModelConfig::getStatus, "1"));

        Long preferDeptId = defaultImage != null ? defaultImage.getDeptId() : null;
        AiModelConfig sameDept = null;
        AiModelConfig anyMatch = null;
        for (AiModelConfig cfg : candidates) {
            if (!supports(cfg, generationMode)) {
                continue;
            }
            if (anyMatch == null) {
                anyMatch = cfg;
            }
            if (sameDept == null && preferDeptId != null && Objects.equals(preferDeptId, cfg.getDeptId())) {
                sameDept = cfg;
            }
        }
        AiModelConfig chosen = sameDept != null ? sameDept : anyMatch;
        if (chosen != null) {
            log.info(">>> [CapabilityRouter] 能力 {} 路由到模型 [{}] provider={}",
                    generationMode, chosen.getName(), chosen.getProvider());
            return chosen;
        }

        // 都不支持 → 回退默认模型（让厂商去试），无默认则返回 null 由调用方给出友好提示
        log.warn(">>> [CapabilityRouter] 未找到声明支持能力 [{}] 的绘图模型，回退默认模型", generationMode);
        return defaultImage;
    }

    /**
     * 判断某模型是否支持指定能力。
     * 兼容策略：imageCapabilities 为空的老模型，视为仅支持 text_to_image / image_to_image。
     */
    public boolean supports(AiModelConfig config, String generationMode) {
        if (config == null) {
            return false;
        }
        if (generationMode == null || generationMode.trim().isEmpty()) {
            return true;
        }
        String caps = config.getImageCapabilities();
        if (caps == null || caps.trim().isEmpty()) {
            return "text_to_image".equals(generationMode)
                    || "image_to_image".equals(generationMode)
                    || "image_edit".equals(generationMode);
        }
        try {
            List<String> list = JSON.parseArray(caps, String.class);
            return list != null && list.contains(generationMode);
        } catch (Exception e) {
            log.warn(">>> [CapabilityRouter] 解析 imageCapabilities 失败, value={}", caps);
            // 解析异常时按逗号分隔兜底
            for (String c : caps.split(",")) {
                if (generationMode.equals(c.trim())) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 汇总当前所有启用 IMAGE 模型声明支持的能力并集（供前端动态渲染能力面板）。
     * 老模型 imageCapabilities 为空时，按兼容策略计入 text_to_image / image_to_image。
     */
    public java.util.Set<String> supportedModes() {
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        List<AiModelConfig> candidates = modelConfigService.list(
                new LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getModelType, ModelType.IMAGE.name())
                        .eq(AiModelConfig::getStatus, "1"));
        for (AiModelConfig cfg : candidates) {
            String caps = cfg.getImageCapabilities();
            if (caps == null || caps.trim().isEmpty()) {
                result.add("text_to_image");
                result.add("image_to_image");
                result.add("image_edit");
                continue;
            }
            try {
                List<String> list = JSON.parseArray(caps, String.class);
                if (list != null) {
                    result.addAll(list);
                }
            } catch (Exception e) {
                for (String c : caps.split(",")) {
                    if (!c.trim().isEmpty()) {
                        result.add(c.trim());
                    }
                }
            }
        }
        return result;
    }
}
