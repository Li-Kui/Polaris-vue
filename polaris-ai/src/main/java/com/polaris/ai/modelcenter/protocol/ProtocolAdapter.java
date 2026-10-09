package com.polaris.ai.modelcenter.protocol;

import java.util.Optional;
import java.util.Set;

/** 描述协议级传输映射；不代表任一 Provider 或模型实际支持这些能力。 */
public interface ProtocolAdapter {

    String protocolCode();

    int adapterVersion();

    Set<String> mappedCapabilities();

    Optional<ProtocolEndpoint> findEndpoint(String capabilityCode);

    Optional<ProtocolEndpoint> modelDiscoveryEndpoint();
}
