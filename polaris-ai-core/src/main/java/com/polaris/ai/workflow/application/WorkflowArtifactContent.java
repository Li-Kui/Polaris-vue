package com.polaris.ai.workflow.application;

/** 经授权返回且不暴露私有存储路径的产物内容。 */
public record WorkflowArtifactContent(
        String fileName,
        String mimeType,
        byte[] content) {

    public WorkflowArtifactContent {
        content = content == null ? new byte[0] : content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
