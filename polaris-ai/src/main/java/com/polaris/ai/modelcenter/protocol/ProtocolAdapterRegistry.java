package com.polaris.ai.modelcenter.protocol;

import java.util.List;
import java.util.Optional;

/** Protocol Code 是代码级扩展点，每个 Code 只能注册一个 Adapter。 */
public interface ProtocolAdapterRegistry {

    ProtocolAdapter getRequired(String protocolCode);

    Optional<ProtocolAdapter> find(String protocolCode);

    List<ProtocolAdapter> list();
}
