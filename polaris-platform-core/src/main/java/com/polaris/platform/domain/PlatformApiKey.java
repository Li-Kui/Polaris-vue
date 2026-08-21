package com.polaris.platform.domain;

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
    private String apiKey;
    private String keyName;
    /** JSON: ["chat","knowledge","agent"] */
    private String permissions;
    private Integer rateLimit;
    private String status;
    private Date expireTime;
    private Date lastUsedTime;
}
