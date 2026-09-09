package com.polaris.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 中台租户用量统计响应
 *
 * @author polaris
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "中台租户用量统计响应")
public class PlatformUsageResponse {

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "租户名称")
    private String tenantName;

    @Schema(description = "Token 配额，-1 表示不限制")
    private Long quotaTokens;

    @Schema(description = "已使用 Token 数")
    private Long usedTokens;

    @Schema(description = "剩余 Token 数，-1 表示不限制")
    private Long remainingTokens;
}
