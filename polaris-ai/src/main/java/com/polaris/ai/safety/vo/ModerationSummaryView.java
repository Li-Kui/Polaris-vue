package com.polaris.ai.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Map;

@Schema(description = "AI敏感内容安全检测统计汇总视图")
public record ModerationSummaryView(
        @Schema(description = "总检测请求量")
        long totalRequests,

        @Schema(description = "放行次数")
        long allowCount,

        @Schema(description = "阻断拦截次数")
        long blockCount,

        @Schema(description = "隔离处置次数")
        long quarantineCount,

        @Schema(description = "替换脱敏次数")
        long replaceCount,

        @Schema(description = "第三方复核调用次数")
        long providerCalls,

        @Schema(description = "第三方缓存命中次数")
        long providerCacheHits,

        @Schema(description = "第三方预估调用总费用 (元)")
        BigDecimal estimatedProviderCost,

        @Schema(description = "当前隔离区存量文档数")
        long activeQuarantineDocuments,

        @Schema(description = "待处理候选敏感词数")
        long pendingCandidates,

        @Schema(description = "违规分类分布 (Category -> Count)")
        Map<String, Long> categoryDistribution,

        @Schema(description = "每日调用与拦截趋势 (Date -> {allow, block, provider})")
        Map<String, Map<String, Long>> dailyTrends
) {}
