package com.polaris.ai.image;

import com.polaris.ai.domain.AiModelConfig;

/**
 * 绘图厂商适配器统一请求对象
 *
 * @author polaris
 */
public class ImageGenRequest {

    /** 使用的模型配置 */
    private AiModelConfig config;
    /** 提示词 */
    private String prompt;
    /** 参考图/垫图 URL（文生图时为 null） */
    private String refImageUrl;
    /** 生成能力（text_to_image / image_to_image ...） */
    private String generationMode;
    /** 出图尺寸，统一用 1024x1024 记法，适配器内部按厂商转换 */
    private String size;
    /** 出图数量 */
    private int n = 1;
    /** 本系统任务 ID（仅日志追踪） */
    private String taskId;
    /** 遮罩图 URL（inpainting/object_removal 需要） */
    private String maskImageUrl;
    /** 多源图 URL 列表（multi_image 等需要；单图时用 refImageUrl 即可） */
    private java.util.List<String> sourceImageUrls;
    /** 负向提示词（可选） */
    private String negativePrompt;
    /** 扩图参数 JSON（仅 outpainting 模式使用） */
    private String expandParams;
    /** 超分倍数（仅 upscale 模式使用），范围 1-4，默认 2 */
    private Integer upscaleFactor;
    /** 透传前端自定义参数（如 pixelExact / presetName 等），供适配器分支判断 */
    private java.util.Map<String, Object> extra;

    public AiModelConfig getConfig() { return config; }
    public void setConfig(AiModelConfig config) { this.config = config; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getRefImageUrl() { return refImageUrl; }
    public void setRefImageUrl(String refImageUrl) { this.refImageUrl = refImageUrl; }
    public String getGenerationMode() { return generationMode; }
    public void setGenerationMode(String generationMode) { this.generationMode = generationMode; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public int getN() { return n; }
    public void setN(int n) { this.n = n; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getMaskImageUrl() { return maskImageUrl; }
    public void setMaskImageUrl(String maskImageUrl) { this.maskImageUrl = maskImageUrl; }
    public java.util.List<String> getSourceImageUrls() { return sourceImageUrls; }
    public void setSourceImageUrls(java.util.List<String> sourceImageUrls) { this.sourceImageUrls = sourceImageUrls; }
    public String getNegativePrompt() { return negativePrompt; }
    public void setNegativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; }
    public String getExpandParams() { return expandParams; }
    public void setExpandParams(String expandParams) { this.expandParams = expandParams; }
    public Integer getUpscaleFactor() { return upscaleFactor; }
    public void setUpscaleFactor(Integer upscaleFactor) { this.upscaleFactor = upscaleFactor; }
    public java.util.Map<String, Object> getExtra() { return extra; }
    public void setExtra(java.util.Map<String, Object> extra) { this.extra = extra; }
}
