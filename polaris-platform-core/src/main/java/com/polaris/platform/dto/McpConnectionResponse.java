package com.polaris.platform.dto;

import com.polaris.common.core.domain.BaseEntity;
import com.polaris.platform.domain.PlatformMcpConnection;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/** 不回显凭证密文、明文或会话标识。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class McpConnectionResponse extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String connectionName;
    private String endpointUrl;
    private String authType;
    private String credentialHeaderName;
    private Boolean credentialConfigured;
    private Integer timeoutMs;
    private String status;
    private Long revision;
    private Long catalogRevision;
    private String discoveryStatus;
    private Date lastDiscoveryTime;
    private Date lastDiscoverySuccessTime;
    private String lastDiscoveryError;

    public static McpConnectionResponse from(PlatformMcpConnection connection) {
        McpConnectionResponse response = new McpConnectionResponse();
        response.setId(connection.getId());
        response.setConnectionName(connection.getConnectionName());
        response.setEndpointUrl(connection.getEndpointUrl());
        response.setAuthType(connection.getAuthType());
        response.setCredentialHeaderName(connection.getCredentialHeaderName());
        response.setCredentialConfigured(connection.getAuthConfig() != null);
        response.setTimeoutMs(connection.getTimeoutMs());
        response.setStatus(connection.getStatus());
        response.setRevision(connection.getRevision());
        response.setCatalogRevision(connection.getCatalogRevision());
        response.setDiscoveryStatus(connection.getDiscoveryStatus());
        response.setLastDiscoveryTime(connection.getLastDiscoveryTime());
        response.setLastDiscoverySuccessTime(connection.getLastDiscoverySuccessTime());
        response.setLastDiscoveryError(connection.getLastDiscoveryError());
        response.setRemark(connection.getRemark());
        response.setCreateTime(connection.getCreateTime());
        response.setUpdateTime(connection.getUpdateTime());
        return response;
    }
}
