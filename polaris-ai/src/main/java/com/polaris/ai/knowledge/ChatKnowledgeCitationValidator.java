package com.polaris.ai.knowledge;

import com.polaris.ai.dto.KnowledgeCitation;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 仅核对约定的方括号来源编号，不判断结论支持度；不调用模型、不改写回答。 */
public final class ChatKnowledgeCitationValidator {
    private ChatKnowledgeCitationValidator() { }
    public record Result(int version, String state, int referencedCount, int invalidCount) { }
    public static Result unverified() {return new Result(1,"UNVERIFIED",0,0);}

    public static Result evaluate(String answer, List<KnowledgeCitation> references) {
        if (references==null || references.isEmpty()) return null;
        if (references.size()>50 || answer==null || answer.length()>200000) return unverified();
        Set<String> allowed=new HashSet<>(), used=new HashSet<>();
        for (var ref:references) {
            if (ref==null || !canonical(ref.citationId()) || !allowed.add(ref.citationId())) return unverified();
        }
        int invalid=0, brackets=0, open=-1;
        for (int cursor=0;cursor<answer.length();cursor++) {
            char current=answer.charAt(cursor);
            if (current=='[') {
                if (++brackets>1000) return unverified();
                if (open>=0 && looksLikeCitation(answer.substring(open+1,Math.min(cursor,open+65)))) invalid++;
                open=cursor;
            } else if (current==']' && open>=0) {
                String body=answer.substring(open+1,Math.min(cursor,open+257)).strip();
                if (looksLikeCitation(body)) {
                    if (cursor-open>257) invalid++;
                    else for (String id:body.split("[\\s,，、;；]+",-1)) {
                        if (canonical(id) && allowed.contains(id)) used.add(id);
                        else invalid++;
                    }
                }
                open=-1;
            }
        }
        if (open>=0 && looksLikeCitation(answer.substring(open+1,Math.min(answer.length(),open+65)))) invalid++;
        return new Result(1,invalid>0?"INVALID_IDS":used.isEmpty()?"MISSING_IDS":"IDS_VALID",used.size(),invalid);
    }

    private static boolean canonical(String id) {return id!=null && id.matches("S([1-9]|[1-4][0-9]|50)");}
    private static boolean looksLikeCitation(String text) {
        String normalized=Normalizer.normalize(text,Normalizer.Form.NFKC);
        StringBuilder visible=new StringBuilder();
        normalized.codePoints().filter(cp->Character.getType(cp)!=Character.FORMAT).forEach(visible::appendCodePoint);
        String value=visible.toString().stripLeading().toUpperCase(Locale.ROOT);
        if (!value.startsWith("S")) return false;
        if (value.length()==1) return true;
        char next=value.charAt(1);
        return Character.isDigit(next) || Character.isWhitespace(next) || ",，、;；:：-".indexOf(next)>=0;
    }
}
