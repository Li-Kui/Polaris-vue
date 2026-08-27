package com.polaris.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 数据源密码请求。 */
@Data
@Schema(description = "数据源密码请求")
public class DatasourceCredentialRequest {

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "数据库密码", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;
}
