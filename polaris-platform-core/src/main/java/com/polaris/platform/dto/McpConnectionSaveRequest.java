package com.polaris.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

@Data
@Schema(description = "MCP 连接保存请求，不接受调用方指定租户")
public class McpConnectionSaveRequest {
    private Long id;
    private Long revision;
    private String connectionName;
    private String endpointUrl;
    private String authType;
    private String credentialHeaderName;

    @Schema(description = "KEEP 保留、REPLACE 替换、CLEAR 清除", allowableValues = {"KEEP", "REPLACE", "CLEAR"})
    private String credentialAction;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String secret;

    private Integer timeoutMs;
    private String status;
    private String remark;
}
