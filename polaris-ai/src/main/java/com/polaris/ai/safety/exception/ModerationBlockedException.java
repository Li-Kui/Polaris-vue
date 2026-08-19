package com.polaris.ai.safety.exception;

/** Safe control-flow signal indicating that no more generated text may be released. */
public class ModerationBlockedException extends RuntimeException {
    private final com.polaris.ai.safety.dto.ModerationResult result;

    public ModerationBlockedException() {
        this("AI content was blocked by moderation", null);
    }

    public ModerationBlockedException(String message) {
        this(message, null);
    }

    public ModerationBlockedException(String message, com.polaris.ai.safety.dto.ModerationResult result) {
        super(message);
        this.result = result;
    }

    public com.polaris.ai.safety.dto.ModerationResult getResult() {
        return result;
    }
}
