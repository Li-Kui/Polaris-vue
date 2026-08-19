package com.polaris.ai.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

@Schema(description = "词库版本视图")
public record DictionaryVersionView(
        @Schema(description = "版本ID")
        Long id,

        @Schema(description = "版本号")
        String versionNo,

        @Schema(description = "状态: DRAFT, PUBLISHED, ARCHIVED")
        String status,

        @Schema(description = "来源版本/说明")
        String sourceVersion,

        @Schema(description = "包含规则总数")
        int ruleCount,

        @Schema(description = "发布人")
        String publishedBy,

        @Schema(description = "发布时间")
        Date publishedTime,

        @Schema(description = "创建时间")
        Date createTime
) {}
