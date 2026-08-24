package com.polaris.platform.dto;

import com.polaris.platform.domain.Tenant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 中台租户创建请求
 *
 * @author polaris
 */
@Data
@Schema(description = "中台租户创建请求")
public class CreateTenantRequest {

    @Valid
    @NotNull(message = "租户信息、初始管理员账号和密码不能为空")
    @Schema(description = "租户信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private Tenant tenant;

    @NotNull(message = "租户信息、初始管理员账号和密码不能为空")
    @Schema(description = "初始管理员用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String adminUsername;

    @NotNull(message = "租户信息、初始管理员账号和密码不能为空")
    @Schema(description = "初始管理员密码", requiredMode = Schema.RequiredMode.REQUIRED,
            accessMode = Schema.AccessMode.WRITE_ONLY)
    private String adminPassword;
}
