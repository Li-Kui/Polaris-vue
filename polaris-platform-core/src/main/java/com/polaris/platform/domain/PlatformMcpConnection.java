package com.polaris.platform.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Date;

/** 租户私有的远程 Streamable HTTP MCP 连接。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "中台 MCP 连接")
public class PlatformMcpConnection extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String connectionName;
    private String endpointUrl;
    private String authType;
    private String credentialHeaderName;

    @JsonIgnore
    @ToString.Exclude
    @Schema(hidden = true)
    private String authConfig;

    private Integer timeoutMs;
    private String status;
    private Long revision;
    private Long catalogEpoch;
    private Long catalogRevision;
    private String discoveryStatus;
    private Date lastDiscoveryTime;
    private Date lastDiscoverySuccessTime;
    private String lastDiscoveryError;
}
