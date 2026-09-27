package com.polaris.ai.modelcenter.schema.options;

/** Options 缓存如在后续启用，必须至少包含这些版本维度。 */
public record SchemaOptionsCacheKey(
        String resolverCode,
        Long connectionId,
        long connectionRevision,
        String modelName,
        String capability,
        String field) {}
