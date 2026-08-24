package com.polaris.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 中台用户登录请求
 *
 * @author polaris
 */
@Data
@Schema(description = "中台用户登录请求")
public class PlatformLoginRequest {

    @NotBlank(message = "租户编码、用户名或密码不能为空")
    @Schema(description = "租户编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tenantCode;

    @NotBlank(message = "租户编码、用户名或密码不能为空")
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "租户编码、用户名或密码不能为空")
    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;
}
