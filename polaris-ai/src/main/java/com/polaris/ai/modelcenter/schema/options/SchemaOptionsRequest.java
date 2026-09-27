package com.polaris.ai.modelcenter.schema.options;

/** Options API 允许客户端提交的最小请求契约。 */
public record SchemaOptionsRequest(
        Long connectionId,
        String modelName,
        String capability,
        String field) {}
