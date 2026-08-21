package com.polaris.platform.domain;

import com.polaris.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 外部数据源实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlatformDatasource extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String dsName;
    /** MYSQL|POSTGRESQL|SQLSERVER */
    private String dsType;
    private String jdbcUrl;
    private String username;
    /** AES加密 */
    private String password;
    private Integer queryTimeout;
    private Boolean readOnly;
    private String extraConfig;
    private String status;
}
