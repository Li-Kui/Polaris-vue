package com.polaris.ai.modelcenter.modeltest;

import java.util.Optional;

/** Capability Test Handler 的只读注册表。 */
public interface CapabilityTestHandlerRegistry {

    CapabilityTestHandler getRequired(String capabilityCode);

    Optional<CapabilityTestHandler> find(String capabilityCode);
}
