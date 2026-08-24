package com.polaris.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * API Key 创建结果
 */
@Data
@AllArgsConstructor
@Schema(description = "中台 API Key 创建响应")
public class PlatformApiKeyCreatedResponse {

    @Schema(description = "API Key 记录 ID")
    private Long id;

    @Schema(description = "API Key 名称")
    private String keyName;

    @Schema(description = "API Key 脱敏前缀")
    private String keyPrefix;

    /** 仅在创建成功时返回一次 */
    @Schema(description = "API Key 明文，仅在创建成功时返回一次", accessMode = Schema.AccessMode.READ_ONLY)
    private String apiKey;
}
