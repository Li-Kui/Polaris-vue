package com.polaris.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * OpenAI 兼容对话消息
 *
 * @author polaris
 */
@Data
@Schema(description = "OpenAI 兼容对话消息")
public class OpenAiMessage {

    @Schema(description = "消息角色", allowableValues = {"system", "user", "assistant"})
    private String role;

    @Schema(description = "消息内容")
    private String content;
}
