package com.polaris.platform.domain;

import com.polaris.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 第三方 API 连接器实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlatformApiConnector extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String connectorName;
    private String baseUrl;
    /** NONE|API_KEY|BEARER|BASIC */
    private String authType;
    /** JSON */
    private String authConfig;
    private String defaultHeaders;
    private Integer timeoutMs;
    private String status;
}
