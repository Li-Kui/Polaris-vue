package com.polaris.ai.modelcenter.vo;

import com.polaris.ai.modelcenter.schema.SchemaProfileDefinition;

import java.util.List;

/** 编辑器只读的服务商元数据，不返回凭据 Schema 与内部 Overlay。 */
public record ProviderProfileEditorVO(
        String layer,
        String code,
        int profileVersion,
        String protocolCode,
        String defaultBaseUrl,
        boolean modelDiscoverySupported,
        List<String> supportedCapabilities) {

    public ProviderProfileEditorVO {
        supportedCapabilities = supportedCapabilities == null
                ? List.of() : List.copyOf(supportedCapabilities);
    }

    public static ProviderProfileEditorVO from(
            SchemaProfileDefinition definition) {
        return new ProviderProfileEditorVO(
                definition.layer().name(), definition.code(),
                definition.profileVersion(), definition.protocolCode(),
                definition.defaultBaseUrl(),
                definition.modelDiscoverySupported(),
                definition.supportedCapabilities());
    }
}
