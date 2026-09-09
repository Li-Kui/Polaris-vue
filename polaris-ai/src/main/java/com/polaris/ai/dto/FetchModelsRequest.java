package com.polaris.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 拉取远程模型列表请求 DTO
 *
 * @author polaris
 */
@Data
@Schema(description = "拉取远程模型列表请求参数")
public class FetchModelsRequest {

    @Schema(description = "提供商标识 (deepseek/dashscope/openai/ollama/ark)")
    private String provider;

    @Schema(description = "API Key")
    private String apiKey;

    @Schema(description = "自定义 API Base URL（可选）")
    private String baseUrl;

    @Schema(description = "连接方式：direct=直连厂商 relay=中转站")
    private String accessMode;
}
