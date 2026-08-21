package com.polaris.ai.tools.base;

/**
 * 智能体工具适用范围
 */
public enum ToolScope {
    /** 全员通用（管理后台与中台均可用） */
    UNIVERSAL,
    /** 中台专属 */
    PLATFORM,
    /** 仅宿主管理后台超管/系统运维可用（中台完全不可见且不可用） */
    ADMIN_ONLY
}
