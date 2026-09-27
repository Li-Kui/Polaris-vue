package com.polaris.ai.runtime.openapi;

/** OpenAPI 对外仅暴露稳定 model_code。 */
public record OpenApiModelDescriptor(
        String modelCode,
        String displayName,
        long revision) {
}
