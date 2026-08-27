package com.polaris.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 新增或修改 API 连接器请求。
 */
@Data
@Schema(description = "保存 API 连接器请求")
public class ApiConnectorSaveRequest {

    @Schema(description = "连接器 ID，修改时必填")
    private Long id;

    @Schema(description = "连接器名称")
    private String connectorName;

    @Schema(description = "连接器基础地址")
    private String baseUrl;

    @Schema(description = "认证类型", allowableValues = {"NONE", "API_KEY", "BEARER"})
    private String authType;

    @Schema(description = "凭证处理方式", allowableValues = {"KEEP", "REPLACE", "CLEAR"})
    private String credentialAction;

    @Schema(description = "新凭证，仅在 REPLACE 时使用")
    private ApiConnectorCredentialRequest credential;

    @Schema(description = "默认请求头")
    private Map<String, String> defaultHeaders;

    @Schema(description = "请求超时时间，单位毫秒")
    private Integer timeoutMs;

    @Schema(description = "连接器状态，0 表示正常")
    private String status;

    @Schema(description = "备注")
    private String remark;
}
