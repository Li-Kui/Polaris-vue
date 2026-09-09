package com.polaris.ai.safety.vo;

import com.polaris.ai.safety.model.ModerationRule;

import java.util.List;

/**
 * 词库版本差异比对视图对象
 *
 * @author polaris
 */
public record DictionaryVersionDiffView(
        Long baseVersionId,
        String baseVersionNo,
        Long targetVersionId,
        String targetVersionNo,
        int addedCount,
        int removedCount,
        int modifiedCount,
        List<ModerationRule> addedRules,
        List<ModerationRule> removedRules,
        List<RuleDiffItem> modifiedRules
) {
    public record RuleDiffItem(
            String content,
            String categoryBefore,
            String categoryAfter,
            String ruleTypeBefore,
            String ruleTypeAfter,
            Integer weightBefore,
            Integer weightAfter
    ) {}
}
