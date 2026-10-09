package com.polaris.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分享清单 DTO
 */
@Schema(description = "工作流分享清单")
public record WorkflowShareManifest(
        @Schema(description = "分享名称")
        String shareName,

        @Schema(description = "工作流编码")
        String workflowCode,

        @Schema(description = "页面类型")
        String pageType,

        @Schema(description = "输入定义")
        Object inputSchema,

        @Schema(description = "页面配置")
        Object pageConfig
) {}
