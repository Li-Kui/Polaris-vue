package com.polaris.ai.modelcenter.schema;

/** 已按固定层级合并并完成 Runtime Hash 的 Schema。 */
public record ResolvedCapabilitySchema(
        CapabilitySchemaDefinition definition,
        String runtimeSchemaHash) {}
