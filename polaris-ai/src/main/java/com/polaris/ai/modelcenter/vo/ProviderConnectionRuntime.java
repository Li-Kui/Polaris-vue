package com.polaris.ai.modelcenter.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

/** 仅供内部 Runtime 使用的 Provider Connection 视图。 */
public record ProviderConnectionRuntime(
        Long id,
        Long tenantId,
        Long deptId,
        String providerCode,
        String protocolCode,
        String networkMode,
        String baseUrl,
        Map<String, Object> extraConfig,
        long revision,
        @JsonIgnore Map<String, Object> credentials
) {
    @Override
    public String toString() {
        return "ProviderConnectionRuntime[id=" + id
                + ", tenantId=" + tenantId
                + ", deptId=" + deptId
                + ", providerCode=" + providerCode
                + ", protocolCode=" + protocolCode
                + ", networkMode=" + networkMode
                + ", baseUrl=" + baseUrl
                + ", extraConfig=" + extraConfig
                + ", revision=" + revision
                + ", credentials=<redacted>]";
    }
}
