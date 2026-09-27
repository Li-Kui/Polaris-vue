package com.polaris.ai.modelcenter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** 新建 Provider Connection 请求。 */
@Schema(description = "新建 Provider Connection 请求")
public record ProviderConnectionCreateRequest(
        @NotBlank
        @Size(max = 100)
        String connectionName,

        @NotBlank
        @Size(max = 50)
        String providerCode,

        @NotBlank
        @Size(max = 50)
        String protocolCode,

        @NotBlank
        @Size(max = 20)
        @Schema(description = "连接方式：DIRECT 直连厂商，RELAY OpenAI 兼容中转；PUBLIC/INTERNAL 为兼容旧值")
        String networkMode,

        @NotBlank
        @Size(max = 500)
        String baseUrl,

        @Size(max = 32)
        @Schema(description = "初始凭据；响应与日志不会回显")
        Map<String, Object> credential,

        @Size(max = 32)
        Map<String, Object> extraConfig,

        Long deptId,

        @Size(max = 1)
        String status,

        @Size(max = 500)
        String remark
) {
    @Override
    public String toString() {
        return "ProviderConnectionCreateRequest[connectionName=" + connectionName
                + ", providerCode=" + providerCode
                + ", protocolCode=" + protocolCode
                + ", networkMode=" + networkMode
                + ", baseUrl=" + baseUrl
                + ", credential=<redacted>"
                + ", extraConfig=" + extraConfig
                + ", deptId=" + deptId
                + ", status=" + status
                + ", remark=" + remark + "]";
    }
}
