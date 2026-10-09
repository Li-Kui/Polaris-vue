package com.polaris.ai.workflow.application;

/** 默认分享配置独立于草稿和发布版本，空值表示使用运行时默认值。 */
public record WorkflowShareDefaultsCommand(
        String defaultPageType,
        String sharePageConfigJson,
        Integer expectedLockVersion) {
}
