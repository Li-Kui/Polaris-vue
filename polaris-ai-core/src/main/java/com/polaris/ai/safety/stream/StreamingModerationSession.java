package com.polaris.ai.safety.stream;

import java.util.List;

/** Stateful, bounded release gate for one AI output stream. */
public interface StreamingModerationSession {
    List<String> append(String chunk);

    List<String> finish();

    String approvedText();

    boolean blocked();
}
