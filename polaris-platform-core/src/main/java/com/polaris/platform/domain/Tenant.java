package com.polaris.platform.domain;

import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "中台租户")
public class Tenant extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "租户名称")
    private String tenantName;

    @Schema(description = "租户唯一编码")
    private String tenantCode;

    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "联系人电话")
    private String contactPhone;

    @Schema(description = "租户状态，0 表示正常")
    private String status;

    @Schema(description = "Token 配额，-1 表示不限制")
    private Long quotaTokens;

    @Schema(description = "已使用 Token 数", accessMode = Schema.AccessMode.READ_ONLY)
    private Long usedTokens;
}
