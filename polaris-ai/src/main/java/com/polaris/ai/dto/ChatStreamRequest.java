package com.polaris.ai.dto;

import java.util.List;

public record ChatStreamRequest(
        Long conversationId,
        String message,
        List<String> attachmentTokens,
        String agentCode,
        Boolean enableSearch
) {}
