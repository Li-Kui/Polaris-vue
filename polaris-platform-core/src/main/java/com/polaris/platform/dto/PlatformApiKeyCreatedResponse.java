package com.polaris.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * API Key 创建结果
 */
@Data
@AllArgsConstructor
public class PlatformApiKeyCreatedResponse {

    private Long id;
    private String keyName;
    private String keyPrefix;
    /** 仅在创建成功时返回一次 */
    private String apiKey;
}
