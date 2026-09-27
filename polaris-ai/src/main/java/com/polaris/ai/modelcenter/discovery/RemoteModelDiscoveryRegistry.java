package com.polaris.ai.modelcenter.discovery;

import java.util.Optional;

/** Remote Model Discovery 的协议级注册表。 */
public interface RemoteModelDiscoveryRegistry {

    RemoteModelDiscovery getRequired(String protocolCode);

    Optional<RemoteModelDiscovery> find(String protocolCode);
}
