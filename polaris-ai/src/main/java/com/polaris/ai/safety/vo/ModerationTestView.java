package com.polaris.ai.safety.vo;

import com.polaris.ai.safety.dto.ModerationMatch;
import com.polaris.ai.safety.model.FinalAction;
import com.polaris.ai.safety.model.LocalDecision;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Set;

@Schema(description = "安全检测测试结果视图")
public record ModerationTestView(
        @Schema(description = "本地判定结果")
        LocalDecision localDecision,

        @Schema(description = "最终执行动作")
        FinalAction finalAction,

        @Schema(description = "综合风险分")
        int riskScore,

        @Schema(description = "命中的违规类别")
        Set<String> matchedCategories,

        @Schema(description = "命中的规则元数据列表(脱敏)")
        List<ModerationMatch> matches,

        @Schema(description = "生效的策略版本号")
        Long policyVersion,

        @Schema(description = "生效的词库版本号")
        Long dictionaryVersion,

        @Schema(description = "是否调用了第三方复核")
        boolean providerUsed,

        @Schema(description = "检测耗时(ms)")
        long latencyMs,

        @Schema(description = "第三方降级原因")
        String fallbackReason
) {}
