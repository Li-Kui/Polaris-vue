package com.polaris.ai.modelcenter.client;

import com.polaris.ai.modelcenter.vo.ProviderConnectionRuntime;

import java.util.Map;

/** Provider Client 的不可变运行身份，日志表示始终隐藏 Credential。 */
public record ProviderRuntimeContext(
        Long connectionId,
        long connectionRevision,
        String providerCode,
        String protocolCode,
        String networkMode,
        String baseUrl,
        Map<String, Object> extraConfig,
        Map<String, Object> credentials) {

    public ProviderRuntimeContext {
        extraConfig = extraConfig == null ? Map.of() : Map.copyOf(extraConfig);
        credentials = credentials == null ? Map.of() : Map.copyOf(credentials);
    }

    public static ProviderRuntimeContext from(ProviderConnectionRuntime runtime) {
        return new ProviderRuntimeContext(
                runtime.id(), runtime.revision(), runtime.providerCode(),
                runtime.protocolCode(), runtime.networkMode(), runtime.baseUrl(),
                runtime.extraConfig(), runtime.credentials());
    }

    @Override
    public String toString() {
        return "ProviderRuntimeContext[connectionId=" + connectionId
                + ", connectionRevision=" + connectionRevision
                + ", providerCode=" + providerCode
                + ", protocolCode=" + protocolCode
                + ", networkMode=" + networkMode
                + ", baseUrl=" + baseUrl
                + ", extraConfig=" + extraConfig
                + ", credentials=<redacted>]";
    }
}
