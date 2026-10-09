package com.polaris.platform.dto;

import java.util.List;

/** 分享附件放在执行请求之外，不要求修改已发布的工作流输入契约。 */
public record WorkflowShareChatRequest(
        Object input,
        String environment,
        List<MessageAttachments> attachments) {

    public record MessageAttachments(int messageIndex, List<String> tokens) {
    }
}
