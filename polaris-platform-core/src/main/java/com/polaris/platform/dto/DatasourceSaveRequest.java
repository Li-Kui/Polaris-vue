package com.polaris.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 新增、修改或测试数据源连接的请求。 */
@Data
@Schema(description = "保存数据源连接请求")
public class DatasourceSaveRequest {
    private Long id;
    private String dsName;
    private String dsType;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    @Schema(description = "密码处理方式", allowableValues = {"KEEP", "REPLACE"})
    private String credentialAction;
    private DatasourceCredentialRequest credential;
    private Boolean sslEnabled;
    private Integer connectTimeoutSeconds;
    private Integer queryTimeoutSeconds;
    private String status;
    private String remark;
}
