package com.polaris.ai.safety.dto;

import java.util.List;

public record PreparedAiInput(
        String displayText,
        String attachmentText,
        List<String> attachmentTokens,
        String attachmentNames,
        ModerationResult moderation
) {
    public boolean isAllowed() {
        return moderation != null && moderation.isAllowed();
    }
}
