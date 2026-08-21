package com.polaris.ai.safety.rule;

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Derives ephemeral ASCII aliases for Chinese risk rules. */
public final class PinyinVariantGenerator {
    public Set<String> variants(String content) {
        if (content == null) {
            return Set.of();
        }
        long chineseCount = content.codePoints().filter(PinyinVariantGenerator::isChinese).count();
        if (chineseCount < 2) {
            return Set.of();
        }

        Set<String> variants = new LinkedHashSet<>();
        addCanonical(variants, PinyinHelper.toPinyin(content, PinyinStyleEnum.NORMAL, ""));
        addCanonical(variants, PinyinHelper.toPinyin(content, PinyinStyleEnum.NUM_LAST, ""));
        if (chineseCount >= 4) {
            addCanonical(variants,
                    PinyinHelper.toPinyin(content, PinyinStyleEnum.FIRST_LETTER, ""));
        }
        return Set.copyOf(variants);
    }

    private static void addCanonical(Set<String> variants, String value) {
        String canonical = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
        if (!canonical.isBlank()) {
            variants.add(canonical);
        }
    }

    private static boolean isChinese(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }
}
