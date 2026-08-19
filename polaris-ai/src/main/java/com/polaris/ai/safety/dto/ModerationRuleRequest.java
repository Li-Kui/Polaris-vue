package com.polaris.ai.safety.dto;

import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.model.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

@Schema(description = "安全词库规则请求")
public record ModerationRuleRequest(
        @Schema(description = "规则ID (新增时留空)")
        Long id,

        @Schema(description = "规则类型: RISK_WORD, RISK_CONTEXT, SAFE_CONTEXT, ALLOW_TERM")
        RuleType ruleType,

        @Schema(description = "词条/短语文本内容")
        String content,

        @Schema(description = "违规分类/类别编码")
        String category,

        @Schema(description = "风险权重分 (-100..100)")
        int weight,

        @Schema(description = "匹配模式: EXACT, CONTAINS, PINYIN")
        String matchMode,

        @Schema(description = "生效场景列表 (空表示全场景)")
        Set<ModerationScene> scenes,

        @Schema(description = "是否启用")
        boolean enabled,

        @Schema(description = "备注说明")
        String remark
) {}
