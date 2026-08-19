package com.polaris.ai.safety.dto;

import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.model.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Set;

@Schema(description = "候选词批量处理请求")
public record CandidateBatchRequest(
        @Schema(description = "待操作的候选词ID列表")
        List<Long> candidateIds,

        @Schema(description = "目标草稿版本ID (采纳时必填)")
        Long targetDraftVersionId,

        @Schema(description = "规则类型 (采纳时必填)")
        RuleType ruleType,

        @Schema(description = "违规分类 (采纳时可选)")
        String category,

        @Schema(description = "风险权重 (采纳时可选)")
        Integer weight,

        @Schema(description = "匹配模式 (采纳时可选)")
        String matchMode,

        @Schema(description = "适用场景 (采纳时可选)")
        Set<ModerationScene> scenes,

        @Schema(description = "拒绝理由 (拒绝时可选)")
        String rejectReason
) {}
