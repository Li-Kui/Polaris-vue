package com.polaris.ai.safety.rule;

import java.util.Arrays;

/** Normalized UTF-16 text together with a lossless mapping to the submitted text. */
public final class NormalizedText {
    private final String text;
    private final int originalLength;
    private final int[] originalStart;
    private final int[] originalEnd;

    public NormalizedText(String text, int originalLength, int[] originalStart, int[] originalEnd) {
        if (text == null || originalStart == null || originalEnd == null) {
            throw new IllegalArgumentException("Normalized text and index maps are required");
        }
        if (text.length() != originalStart.length || text.length() != originalEnd.length) {
            throw new IllegalArgumentException("Every normalized UTF-16 unit must have an original span");
        }
        for (int i = 0; i < text.length(); i++) {
            if (originalStart[i] < 0 || originalStart[i] >= originalEnd[i]
                    || originalEnd[i] > originalLength) {
                throw new IllegalArgumentException("Invalid original span at normalized index " + i);
            }
        }
        this.text = text;
        this.originalLength = originalLength;
        this.originalStart = Arrays.copyOf(originalStart, originalStart.length);
        this.originalEnd = Arrays.copyOf(originalEnd, originalEnd.length);
    }

    public String text() {
        return text;
    }

    public int originalLength() {
        return originalLength;
    }

    public int originalStart(int normalizedIndex) {
        return originalStart[normalizedIndex];
    }

    public int originalEnd(int normalizedIndex) {
        return originalEnd[normalizedIndex];
    }

    public int originalStartForRange(int normalizedStart) {
        if (normalizedStart == text.length()) {
            return originalLength;
        }
        return originalStart(normalizedStart);
    }

    public int originalEndForRange(int normalizedEndExclusive) {
        if (normalizedEndExclusive == 0) {
            return 0;
        }
        return originalEnd(normalizedEndExclusive - 1);
    }
}
