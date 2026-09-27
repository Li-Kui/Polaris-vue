package com.polaris.ai.modelcenter.modeltest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.modelcenter.client.ProviderRuntimeContext;
import com.polaris.ai.modelcenter.schema.ResolvedCapabilitySchema;

/** Handler 收到的后端可信、已规范化测试上下文。 */
public record ModelTestContext(
        ProviderRuntimeContext provider,
        String modelName,
        ResolvedCapabilitySchema schema,
        ObjectNode capabilityConfig,
        ModelRuntimePolicyDraft runtimePolicy,
        String testCapability) {
}
