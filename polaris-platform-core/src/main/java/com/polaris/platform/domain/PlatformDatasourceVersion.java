package com.polaris.platform.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 数据源不可变连接版本。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "中台数据源连接版本")
public class PlatformDatasourceVersion extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long datasourceId;
    private Integer versionNo;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    @JsonIgnore
    @Schema(hidden = true)
    private String password;
    private Boolean sslEnabled;
    private Integer connectTimeoutSeconds;
    private Integer queryTimeoutSeconds;
    private String verificationStatus;
    private String databaseProductName;
    private String databaseProductVersion;
    private java.util.Date lastTestTime;
    private Long lastTestLatencyMs;
    private String lastTestErrorCode;
}
