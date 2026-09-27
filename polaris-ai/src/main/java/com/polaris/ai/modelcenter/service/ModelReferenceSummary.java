package com.polaris.ai.modelcenter.service;

/** 删除模型前需要检查的外部业务引用统计。 */
public record ModelReferenceSummary(
        long agents,
        long knowledgeBases,
        long conversations,
        long imageTasks,
        long workflowBindings,
        long defaults) {

    public long total() {
        return agents + knowledgeBases + conversations + imageTasks
                + workflowBindings + defaults;
    }

    public boolean hasReferences() {
        return total() > 0;
    }
}
