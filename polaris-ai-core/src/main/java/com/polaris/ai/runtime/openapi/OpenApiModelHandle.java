package com.polaris.ai.runtime.openapi;

import dev.langchain4j.model.chat.StreamingChatModel;

import java.util.Objects;

/** 权限校验完成后的 OpenAPI 模型句柄。 */
public record OpenApiModelHandle(
        OpenApiModelDescriptor descriptor,
        StreamingChatModel model) {

    public OpenApiModelHandle {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        model = Objects.requireNonNull(model, "model");
    }
}
