package com.polaris.ai.tools.base;

/**
 * 智能体工具外部依赖条件
 */
public enum ToolRequirement {
    /** 无特殊依赖，常驻可用 */
    NONE,
    /** 依赖已启用且可用的 AI 图像生成模型 */
    IMAGE_MODEL,
    /** 依赖底座大模型配置了联网检索 API Key */
    SEARCH_KEY
}
