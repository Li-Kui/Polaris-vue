package com.polaris.platform.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * API Key 实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "中台 API Key")
public class PlatformApiKey extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "API Key 记录 ID")
    private Long id;

    @Schema(description = "所属租户 ID")
    private Long tenantId;

    /** SHA-256 摘要，不向接口响应暴露 */
    @JsonIgnore
    @Schema(hidden = true)
    private String apiKeyHash;

    /** 用于列表脱敏展示 */
    @Schema(description = "API Key 脱敏前缀", accessMode = Schema.AccessMode.READ_ONLY)
    private String keyPrefix;

    @Schema(description = "API Key 名称")
    private String keyName;

    /** JSON: ["chat","knowledge","agent"] */
    @Schema(description = "权限列表 JSON")
    private String permissions;

    @Schema(description = "每分钟请求次数限制")
    private Integer rateLimit;

    @Schema(description = "API Key 状态，0 表示正常")
    private String status;

    @Schema(description = "过期时间")
    private Date expireTime;

    @Schema(description = "最后使用时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date lastUsedTime;
}
