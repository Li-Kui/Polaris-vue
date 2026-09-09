package com.polaris.ai.safety.rule;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Applies deterministic moderation normalization without losing original UTF-16 spans. */
public final class TextNormalizer {
    private static final Map<Integer, String> DEFAULT_NUMERIC_SUBSTITUTIONS = Map.of(
            (int) '4', "a"
    );

    private final Map<Integer, String> numericSubstitutions;
    public TextNormalizer() {
        this(DEFAULT_NUMERIC_SUBSTITUTIONS);
    }

    public TextNormalizer(Map<Integer, String> numericSubstitutions) {
        this.numericSubstitutions = Map.copyOf(numericSubstitutions);
    }

    public NormalizedText normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Raw text is required");
        }

        List<Unit> units = new ArrayList<>();
        for (int originalStart = 0; originalStart < raw.length();) {
            int codePoint = raw.codePointAt(originalStart);
            int originalEnd = originalStart + Character.charCount(codePoint);
            if (!isZeroWidth(codePoint)) {
                String normalized = Normalizer.normalize(
                        new String(Character.toChars(codePoint)), Normalizer.Form.NFKC)
                        .toLowerCase(Locale.ROOT);
                int spanStart = originalStart;
                int spanEnd = originalEnd;
                normalized.codePoints().forEach(value ->
                        units.add(new Unit(value, spanStart, spanEnd)));
            }
            originalStart = originalEnd;
        }

        List<Unit> collapsed = collapseLongRepeats(substituteConfiguredDigits(units));
        StringBuilder text = new StringBuilder();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        for (Unit unit : collapsed) {
            char[] chars = Character.toChars(unit.codePoint());
            text.append(chars);
            for (int i = 0; i < chars.length; i++) {
                starts.add(unit.originalStart());
                ends.add(unit.originalEnd());
            }
        }
        return new NormalizedText(text.toString(), raw.length(), toArray(starts), toArray(ends));
    }

    /**
     * Authoritative matcher normalization, retaining the same original-position map while
     * removing only mechanical connectors inside Han words. Canonical normalization remains
     * stable for persisted dictionary hashes and historical versions.
     */
    public NormalizedText normalizeForMatching(String raw) {
        NormalizedText base = normalize(raw);
        String text = base.text();
        StringBuilder matching = new StringBuilder(text.length());
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            int end = index + Character.charCount(codePoint);
            if (!isHanInternalConnector(text, index, end, codePoint)) {
                matching.appendCodePoint(codePoint);
                for (int unit = index; unit < end; unit++) {
                    starts.add(base.originalStart(unit));
                    ends.add(base.originalEnd(unit));
                }
            }
            index = end;
        }
        return new NormalizedText(matching.toString(), raw.length(),
                toArray(starts), toArray(ends));
    }

    /**
     * Returns the original UTF-16 offset of an unfinished {@code Han + connectors} suffix,
     * or {@code -1} when the suffix cannot become a Han-internal mechanical obfuscation.
     */
    public static int pendingHanConnectorStart(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Raw text is required");
        }
        NormalizedText canonical = new TextNormalizer().normalize(raw);
        String text = canonical.text();
        int connectorStart = text.length();
        while (connectorStart > 0) {
            int codePoint = text.codePointBefore(connectorStart);
            if (!isMechanicalConnector(codePoint)) {
                break;
            }
            connectorStart -= Character.charCount(codePoint);
        }
        if (connectorStart == text.length() || connectorStart == 0) {
            return -1;
        }
        int leading = text.codePointBefore(connectorStart);
        int leadingStart = connectorStart - Character.charCount(leading);
        return isHan(leading) ? canonical.originalStart(leadingStart) : -1;
    }

    private List<Unit> substituteConfiguredDigits(List<Unit> units) {
        List<Unit> result = new ArrayList<>(units.size());
        for (int index = 0; index < units.size(); index++) {
            Unit unit = units.get(index);
            String replacement = numericSubstitutions.get(unit.codePoint());
            if (replacement != null && index > 0 && index + 1 < units.size()
                    && isAsciiLetter(units.get(index - 1).codePoint())
                    && isAsciiLetter(units.get(index + 1).codePoint())) {
                replacement.codePoints().forEach(codePoint -> result.add(new Unit(
                        codePoint, unit.originalStart(), unit.originalEnd())));
            } else {
                result.add(unit);
            }
        }
        return result;
    }

    private static boolean isAsciiLetter(int value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z';
    }

    private static List<Unit> collapseLongRepeats(List<Unit> units) {
        List<Unit> result = new ArrayList<>(units.size());
        for (int index = 0; index < units.size();) {
            int runEnd = index + 1;
            while (runEnd < units.size()
                    && units.get(runEnd).codePoint() == units.get(index).codePoint()) {
                runEnd++;
            }
            if (runEnd - index >= 3 && isHan(units.get(index).codePoint())) {
                result.add(new Unit(units.get(index).codePoint(),
                        units.get(index).originalStart(), units.get(runEnd - 1).originalEnd()));
            } else {
                result.addAll(units.subList(index, runEnd));
            }
            index = runEnd;
        }
        return result;
    }

    private static boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    private static boolean isZeroWidth(int codePoint) {
        return codePoint == 0x200B || codePoint == 0x200C || codePoint == 0x200D
                || codePoint == 0x2060 || codePoint == 0xFEFF;
    }

    private static boolean isHanInternalConnector(String raw, int start, int end,
                                                   int codePoint) {
        if (!isMechanicalConnector(codePoint) || start == 0 || end >= raw.length()) {
            return false;
        }
        int beforeRun = start;
        while (beforeRun > 0) {
            int previous = raw.codePointBefore(beforeRun);
            if (!isMechanicalConnector(previous)) {
                break;
            }
            beforeRun -= Character.charCount(previous);
        }
        int afterRun = end;
        while (afterRun < raw.length()) {
            int next = raw.codePointAt(afterRun);
            if (!isMechanicalConnector(next)) {
                break;
            }
            afterRun += Character.charCount(next);
        }
        if (beforeRun == 0 || afterRun >= raw.length()) {
            return false;
        }
        int previous = raw.codePointBefore(beforeRun);
        int next = raw.codePointAt(afterRun);
        return isHan(previous) && isHan(next);
    }

    private static boolean isMechanicalConnector(int codePoint) {
        return codePoint == '-' || codePoint == '_'
                || codePoint == 0xFF0D || codePoint == 0xFE63
                || codePoint == 0x2010 || codePoint == 0x2011
                || codePoint == 0x2012 || codePoint == 0x2013
                || codePoint == 0x2014;
    }

    private static int[] toArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private record Unit(int codePoint, int originalStart, int originalEnd) {
    }
}
