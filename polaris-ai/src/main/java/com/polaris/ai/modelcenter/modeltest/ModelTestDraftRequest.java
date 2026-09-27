package com.polaris.ai.modelcenter.modeltest;

import java.util.LinkedHashMap;
import java.util.Map;

/** 保存前测试请求；Schema、Profile、Credential 和 Provider 地址均由后端加载。 */
public record ModelTestDraftRequest(
        Long connectionId,
        String modelName,
        String capabilityCode,
        Integer schemaVersion,
        Map<String, Object> capabilityConfig,
        ModelRuntimePolicyDraft runtimePolicy,
        String testCapability) {

    public ModelTestDraftRequest {
        Map<String, Object> compact = new LinkedHashMap<>();
        if (capabilityConfig != null) {
            capabilityConfig.forEach((key, value) -> {
                if (key != null && value != null) {
                    compact.put(key, value);
                }
            });
        }
        capabilityConfig = Map.copyOf(compact);
    }

    @Override
    public String toString() {
        return "ModelTestDraftRequest[connectionId=" + connectionId
                + ", modelName=" + modelName
                + ", capabilityCode=" + capabilityCode
                + ", schemaVersion=" + schemaVersion
                + ", capabilityConfig=<redacted>"
                + ", runtimePolicy=" + runtimePolicy
                + ", testCapability=" + testCapability + "]";
    }
}
