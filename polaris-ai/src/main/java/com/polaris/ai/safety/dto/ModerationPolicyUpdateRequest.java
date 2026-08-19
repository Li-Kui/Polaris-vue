package com.polaris.ai.safety.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "安全检测策略更新请求")
public record ModerationPolicyUpdateRequest(
        @Schema(description = "预设模式: LENIENT, BALANCED, STRICT")
        String preset,

        @Schema(description = "运行模式: OBSERVE, ENFORCE")
        String mode,

        @Schema(description = "是否启用")
        Boolean enabled,

        @Schema(description = "疑似阈值(0-100)")
        Integer suspectThreshold,

        @Schema(description = "拦截阈值(0-100)")
        Integer blockThreshold,

        @Schema(description = "是否启用第三方复核")
        Boolean providerEnabled,

        @Schema(description = "第三方超时时间(ms)")
        Integer providerTimeoutMs,

        @Schema(description = "第三方每日调用限额")
        Integer providerDailyLimit,

        @Schema(description = "第三方每月预算额度")
        BigDecimal providerMonthlyBudget,

        @Schema(description = "文本分段长度")
        Integer segmentChars,

        @Schema(description = "分段重叠长度")
        Integer segmentOverlapChars,

        @Schema(description = "输出缓冲区大小")
        Integer outputBufferChars,

        @Schema(description = "隔离区保存天数(1-90)")
        Integer quarantineDays
) {}
