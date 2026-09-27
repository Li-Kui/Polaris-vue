package com.polaris.ai.modelcenter.schema.options;

/** 动态选项的稳定、安全输出。 */
public record SchemaOption(
        String value,
        String label,
        boolean disabled) {}
