package com.polaris.platform.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 中台租户用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "中台租户用户")
public class PlatformUser extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 ID")
    private Long id;

    @Schema(description = "所属租户 ID")
    private Long tenantId;

    @Schema(description = "用户名")
    private String username;

    /** 仅用于请求反序列化和内部认证，不向接口响应暴露 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "用户密码", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "电子邮箱")
    private String email;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "头像地址")
    private String avatar;

    /** admin/member */
    @Schema(description = "租户角色", allowableValues = {"admin", "member"})
    private String role;

    @Schema(description = "用户状态，0 表示正常")
    private String status;

    @Schema(description = "最后登录时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date lastLoginTime;

    /** 关联租户信息（非数据库字段） */
    @Schema(description = "关联租户信息", accessMode = Schema.AccessMode.READ_ONLY)
    private transient Tenant tenant;
}
