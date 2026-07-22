package com.polaris.ai.enums;

import java.util.Arrays;

/**
 * 图像生成能力枚举（10种）
 * 与 ModelType.IMAGE 是不同维度：ModelType 描述"模型用途"，本枚举描述"具体生成能力"。
 * needsSource：是否需要输入源图；needsMask：是否需要遮罩图。
 *
 * @author polaris
 */
public enum ImageGenerationMode {

    // ===== 生成类 =====
    TEXT_TO_IMAGE("text_to_image", "文生图", false, false),
    IMAGE_TO_IMAGE("image_to_image", "图生图", true, false),
    MULTI_IMAGE("multi_image", "多图生成", true, false),

    // ===== 编辑类 =====
    IMAGE_EDIT("image_edit", "指令改图", true, false),
    INPAINTING("inpainting", "局部重绘", true, true),
    OBJECT_REMOVAL("object_removal", "消除", true, true),
    OUTPAINTING("outpainting", "扩图", true, false),
    BACKGROUND_REPLACEMENT("background_replacement", "换背景", true, false),

    // ===== 增强类 =====
    UPSCALE("upscale", "高清放大", true, false),
    RESTORATION("restoration", "照片修复", true, false);

    private final String code;
    private final String label;
    private final boolean needsSource;
    private final boolean needsMask;

    ImageGenerationMode(String code, String label, boolean needsSource, boolean needsMask) {
        this.code = code;
        this.label = label;
        this.needsSource = needsSource;
        this.needsMask = needsMask;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }
    public boolean isNeedsSource() { return needsSource; }
    public boolean isNeedsMask() { return needsMask; }

    /** 根据 code 解析，找不到返回 null */
    public static ImageGenerationMode fromCode(String code) {
        return Arrays.stream(values())
                .filter(m -> m.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
