package com.polaris.ai.image;

import java.util.List;

/**
 * 绘图提交命令：聊天工具与直连接口统一入参
 *
 * @author polaris
 */
public class ImageGenCommand {

    /** 提示词 */
    private String prompt;
    /** 负向提示词（可选） */
    private String negativePrompt;
    /** 生成能力 code：text_to_image / image_to_image / inpainting ... */
    private String generationMode;
    /** 源图/垫图 URL 列表 */
    private List<String> sourceImages;
    /** 遮罩图 URL（局部重绘/消除时需要） */
    private String maskImage;
    /** 出图尺寸，统一 1024x1024 记法；空则用模型默认 */
    private String size;
    /** 出图数量 */
    private int n = 1;
    /** 关联会话 ID，直连绘图时为 null */
    private Long conversationId;
    /** 扩图参数 JSON（仅 outpainting 模式使用），格式：{"top_scale":1.5,"bottom_scale":1.5,"left_scale":1.5,"right_scale":1.5} */
    private String expandParams;
    /** 超分倍数（仅 upscale 模式使用），范围 1-4，默认 2 */
    private Integer upscaleFactor;
    /** 透传前端自定义参数（如 pixelExact=true 表示前端已做像素级精确替换，后端仅做低强度精修） */
    private java.util.Map<String, Object> extra;

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getNegativePrompt() { return negativePrompt; }
    public void setNegativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; }
    public String getGenerationMode() { return generationMode; }
    public void setGenerationMode(String generationMode) { this.generationMode = generationMode; }
    public List<String> getSourceImages() { return sourceImages; }
    public void setSourceImages(List<String> sourceImages) { this.sourceImages = sourceImages; }
    public String getMaskImage() { return maskImage; }
    public void setMaskImage(String maskImage) { this.maskImage = maskImage; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public int getN() { return n; }
    public void setN(int n) { this.n = n; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getExpandParams() { return expandParams; }
    public void setExpandParams(String expandParams) { this.expandParams = expandParams; }
    public Integer getUpscaleFactor() { return upscaleFactor; }
    public void setUpscaleFactor(Integer upscaleFactor) { this.upscaleFactor = upscaleFactor; }
    public java.util.Map<String, Object> getExtra() { return extra; }
    public void setExtra(java.util.Map<String, Object> extra) { this.extra = extra; }
}
