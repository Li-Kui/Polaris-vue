package com.polaris.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 中台用户登录响应
 *
 * @author polaris
 */
@Data
@AllArgsConstructor
@Schema(description = "中台用户登录响应")
public class PlatformLoginResponse {

    @Schema(description = "中台访问令牌")
    private String token;

    @Schema(description = "当前登录用户信息")
    private PlatformUserResponse user;
}
