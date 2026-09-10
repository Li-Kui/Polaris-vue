package com.polaris.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatStreamRequest(
        @NotNull(message = "会话 ID 不能为空")
        Long conversationId,
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 50000, message = "消息内容不能超过 50000 个字符")
        String message,
        List<String> attachmentTokens,
        String agentCode,
        Boolean enableSearch
) {}
