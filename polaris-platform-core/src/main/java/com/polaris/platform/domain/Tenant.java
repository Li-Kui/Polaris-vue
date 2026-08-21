package com.polaris.platform.domain;

import com.polaris.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Tenant extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private String tenantName;
    private String tenantCode;
    private String contactName;
    private String contactPhone;
    private String status;
    private Long quotaTokens;
    private Long usedTokens;
}
