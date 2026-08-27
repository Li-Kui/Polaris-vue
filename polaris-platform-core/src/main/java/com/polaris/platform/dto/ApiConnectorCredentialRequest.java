package com.polaris.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * API 连接器凭证请求。
 */
@Data
@Schema(description = "API 连接器凭证请求")
public class ApiConnectorCredentialRequest {

    @Schema(description = "API Key 使用的请求头名称")
    private String headerName;

    @Schema(description = "API Key 或 Bearer Token", accessMode = Schema.AccessMode.WRITE_ONLY)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String secret;
}
