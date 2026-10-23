package com.polaris.ai.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 原回答的字面编号位置，不判断主张、支持度或语义，不选择“最近的”引用。 */
public final class KnowledgeCitationOccurrences {
    private static final Pattern BRACKET = Pattern.compile("\\[([^\\[\\]\\n]{1,256})\\]");
    private static final Pattern MEMBER = Pattern.compile("(?:^|[\\s,，、;；]+)(S(?:[1-9]|[1-4][0-9]|50))(?=$|[\\s,，、;；])");
    private KnowledgeCitationOccurrences() { }
    public record Position(int markerStart, int markerEnd, int memberStart, int memberEnd) { }
    public record Report(int version, boolean complete, List<Position> positions, boolean evidenceSupportVerified) { }

    public static Report inspect(String answer, String id) {
        if (answer == null || answer.length() > 200000 || id == null || !id.matches("S(?:[1-9]|[1-4][0-9]|50)"))
            return new Report(1, false, List.of(), false);
        List<Position> result = new ArrayList<>();
        var brackets = BRACKET.matcher(answer);
        int scanned = 0;
        while (brackets.find()) {
            if (++scanned > 1000 || result.size() >= 100) return new Report(1, false, List.copyOf(result), false);
            var members = MEMBER.matcher(brackets.group(1));
            while (members.find()) if (id.equals(members.group(1))) {
                if (result.size() >= 100) return new Report(1, false, List.copyOf(result), false);
                result.add(new Position(brackets.start(), brackets.end(), brackets.start(1) + members.start(1), brackets.start(1) + members.end(1)));
            }
        }
        return new Report(1, true, List.copyOf(result), false);
    }
}
