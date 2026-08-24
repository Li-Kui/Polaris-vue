package com.polaris.platform.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.polaris.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 中台租户用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlatformUser extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String username;
    /** 仅用于请求反序列化和内部认证，不向接口响应暴露 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    /** admin/member */
    private String role;
    private String status;
    private Date lastLoginTime;

    /** 关联租户信息（非数据库字段） */
    private transient Tenant tenant;
}
