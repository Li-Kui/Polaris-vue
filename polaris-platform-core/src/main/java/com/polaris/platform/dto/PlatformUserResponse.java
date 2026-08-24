package com.polaris.platform.dto;

import com.polaris.common.core.domain.BaseEntity;
import com.polaris.platform.domain.PlatformUser;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 中台租户用户响应
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlatformUserResponse extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private String role;
    private String status;
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
