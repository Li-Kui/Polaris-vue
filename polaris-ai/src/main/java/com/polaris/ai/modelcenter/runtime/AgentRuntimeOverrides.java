package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.domain.AiAgent;
import com.polaris.ai.modelcenter.schema.ParameterPolicyDefinition;

import java.util.List;
import java.util.Map;

/** 将智能体级动态参数转换为统一 Runtime 覆盖。 */
public final class AgentRuntimeOverrides {

    private AgentRuntimeOverrides() {
    }

    public static List<CapabilityParameterOverride> from(AiAgent agent) {
        if (agent == null || agent.getTemperature() == null) {
            return List.of();
        }
        return List.of(new CapabilityParameterOverride(
                ParameterPolicyDefinition.ParameterSource.AGENT,
                Map.of("temperature", agent.getTemperature()),
                Map.of()));
    }
}
