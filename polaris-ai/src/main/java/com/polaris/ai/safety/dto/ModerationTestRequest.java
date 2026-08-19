package com.polaris.ai.safety.dto;

import com.polaris.ai.safety.model.ModerationScene;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "安全检测测试句请求")
public record ModerationTestRequest(
        @Schema(description = "待测文本内容")
        String text,

        @Schema(description = "测试场景: CHAT_INPUT, AI_OUTPUT, KNOWLEDGE, AGENT_TOOL, REPORT")
        ModerationScene scene,

        @Schema(description = "是否调用第三方复核 (默认 false)")
        Boolean useProvider
) {}
