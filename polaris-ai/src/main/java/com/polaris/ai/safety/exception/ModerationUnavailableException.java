package com.polaris.ai.safety.exception;

import com.polaris.ai.safety.dto.ModerationResult;

import java.util.Objects;

/** Internal typed failure that can carry the already-decided safe result. */
public class ModerationUnavailableException extends RuntimeException {
    private final ModerationResult safeResult;

    public ModerationUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.safeResult = null;
    }

    public ModerationUnavailableException(String message, ModerationResult safeResult,
                                          Throwable cause) {
        super(message, cause);
        this.safeResult = Objects.requireNonNull(safeResult, "safeResult");
    }

    public ModerationResult safeResult() {
        return Objects.requireNonNull(safeResult, "No safe moderation result is attached");
    }

    public ModerationUnavailableException withSafeResult(ModerationResult result) {
        return new ModerationUnavailableException(getMessage(), result, getCause());
    }
}
