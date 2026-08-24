package com.polaris.platform.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.polaris.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * API Key 实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlatformApiKey extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    /** SHA-256 摘要，不向接口响应暴露 */
    @JsonIgnore
    private String apiKeyHash;
    /** 用于列表脱敏展示 */
    private String keyPrefix;
    private String keyName;
    /** JSON: ["chat","knowledge","agent"] */
    private String permissions;
    private Integer rateLimit;
    private String status;
    private Date expireTime;
    private Date lastUsedTime;
}
