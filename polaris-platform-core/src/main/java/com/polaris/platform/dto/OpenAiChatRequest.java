package com.polaris.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * OpenAI 兼容对话补全请求
 *
 * @author polaris
 */
@Data
@Schema(description = "OpenAI 兼容对话补全请求")
public class OpenAiChatRequest {

    @Schema(description = "模型名称")
    private String model;

    @Schema(description = "对话消息列表")
    private List<OpenAiMessage> messages;

    @Schema(description = "是否使用 SSE 流式响应", defaultValue = "false")
    private Boolean stream;

    @Schema(description = "采样温度")
    private Double temperature;

    @JsonProperty("max_tokens")
    @Schema(description = "最大输出 Token 数")
    private Integer maxTokens;
}
