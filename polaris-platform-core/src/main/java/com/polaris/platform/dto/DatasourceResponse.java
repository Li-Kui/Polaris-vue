package com.polaris.platform.dto;

import com.polaris.common.core.domain.BaseEntity;
import com.polaris.platform.domain.PlatformDatasource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 不包含数据库密码的数据源安全响应。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "数据源安全响应")
public class DatasourceResponse extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String dsName;
    private String dsType;
    private Long currentVersionId;
    private Integer configVersion;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private Boolean passwordConfigured;
    private Boolean sslEnabled;
    private Integer connectTimeoutSeconds;
    private Integer queryTimeoutSeconds;
    private String verificationStatus;
    private String databaseProductName;
    private String databaseProductVersion;
    private java.util.Date lastTestTime;
    private Long lastTestLatencyMs;
    private String lastTestErrorCode;
    private String status;

    public static DatasourceResponse from(PlatformDatasource datasource) {
        if (datasource == null) return null;
        DatasourceResponse response = new DatasourceResponse();
        response.setId(datasource.getId());
        response.setTenantId(datasource.getTenantId());
        response.setDsName(datasource.getDsName());
        response.setDsType(datasource.getDsType());
        response.setCurrentVersionId(datasource.getCurrentVersionId());
        response.setConfigVersion(datasource.getConfigVersion());
        response.setHost(datasource.getHost());
        response.setPort(datasource.getPort());
        response.setDatabaseName(datasource.getDatabaseName());
        response.setUsername(datasource.getUsername());
        response.setPasswordConfigured(datasource.getPassword() != null
                && !datasource.getPassword().isBlank());
        response.setSslEnabled(Boolean.TRUE.equals(datasource.getSslEnabled()));
        response.setConnectTimeoutSeconds(datasource.getConnectTimeoutSeconds());
        response.setQueryTimeoutSeconds(datasource.getQueryTimeoutSeconds());
        response.setVerificationStatus(datasource.getVerificationStatus());
        response.setDatabaseProductName(datasource.getDatabaseProductName());
        response.setDatabaseProductVersion(datasource.getDatabaseProductVersion());
        response.setLastTestTime(datasource.getLastTestTime());
        response.setLastTestLatencyMs(datasource.getLastTestLatencyMs());
        response.setLastTestErrorCode(datasource.getLastTestErrorCode());
        response.setStatus(datasource.getStatus());
        response.setCreateBy(datasource.getCreateBy());
        response.setCreateTime(datasource.getCreateTime());
        response.setUpdateBy(datasource.getUpdateBy());
        response.setUpdateTime(datasource.getUpdateTime());
        response.setRemark(datasource.getRemark());
        return response;
    }
}
