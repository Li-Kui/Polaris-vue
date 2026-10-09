package com.polaris.ai.modelcenter.vo;

import java.util.List;

/** Model Editor 初始化所需的安全上下文。 */
public record ModelEditorContextVO(
        List<ProviderConnectionVO> connections,
        List<CapabilitySchemaEditorVO> capabilitySchemas,
        List<ProviderProfileEditorVO> providerProfiles) {

    public ModelEditorContextVO {
        connections = connections == null ? List.of() : List.copyOf(connections);
        capabilitySchemas = capabilitySchemas == null ? List.of()
                : List.copyOf(capabilitySchemas);
        providerProfiles = providerProfiles == null ? List.of()
                : List.copyOf(providerProfiles);
    }
}
