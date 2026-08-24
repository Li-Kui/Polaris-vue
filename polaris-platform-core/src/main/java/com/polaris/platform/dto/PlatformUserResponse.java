package com.polaris.platform.dto;

import com.polaris.common.core.domain.BaseEntity;
import com.polaris.platform.domain.PlatformUser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 中台租户用户响应
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "中台租户用户响应")
public class PlatformUserResponse extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 ID")
    private Long id;

    @Schema(description = "所属租户 ID")
    private Long tenantId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "电子邮箱")
    private String email;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "头像地址")
    private String avatar;

    @Schema(description = "租户角色", allowableValues = {"admin", "member"})
    private String role;

    @Schema(description = "用户状态，0 表示正常")
    private String status;

    @Schema(description = "最后登录时间")
    private Date lastLoginTime;

    public static PlatformUserResponse from(PlatformUser user) {
        if (user == null) {
            return null;
        }
        PlatformUserResponse response = new PlatformUserResponse();
        response.setId(user.getId());
        response.setTenantId(user.getTenantId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setAvatar(user.getAvatar());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setLastLoginTime(user.getLastLoginTime());
        response.setCreateBy(user.getCreateBy());
        response.setCreateTime(user.getCreateTime());
        response.setUpdateBy(user.getUpdateBy());
        response.setUpdateTime(user.getUpdateTime());
        response.setRemark(user.getRemark());
        return response;
    }
}
