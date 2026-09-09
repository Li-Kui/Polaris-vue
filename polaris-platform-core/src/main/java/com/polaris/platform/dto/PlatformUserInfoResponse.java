package com.polaris.platform.dto;

import com.polaris.platform.domain.Tenant;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 中台当前登录用户信息响应
 *
 * @author polaris
 */
@Data
@AllArgsConstructor
@Schema(description = "中台当前登录用户信息响应")
public class PlatformUserInfoResponse {

    @Schema(description = "当前登录用户信息")
    private PlatformUserResponse user;

    @Schema(description = "当前用户所属租户")
    private Tenant tenant;

    @Schema(description = "当前用户权限标识列表")
    private String[] permissions;
}
