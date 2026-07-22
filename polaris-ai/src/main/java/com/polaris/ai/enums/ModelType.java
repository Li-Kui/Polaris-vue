package com.polaris.ai.enums;

/**
 * AI 模型类型用途枚举
 * 
 * @author polaris
 */
public enum ModelType {
    /**
     * 文本对话模型
     */
    CHAT,

    /**
     * 向量化提取模型
     */
    EMBEDDING,

    /**
     * 图像生成模型 (文生图)
     */
    IMAGE,

    /**
     * 语音模型 (TTS/ASR)
     */
    SPEECH,

    /**
     * 视频生成模型
     */
    VIDEO
}
