package com.polaris.platform.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.polaris.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Date;

/** 工具不会物理删除，保留失踪状态与后续调用/流程引用。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlatformMcpTool extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long tenantId;
    private Long connectionId;
    private String toolName;
    private String toolTitle;
    private String description;
    @ToString.Exclude
    private String inputSchema;
    @ToString.Exclude
    private String outputSchema;
    @JsonIgnore
    @ToString.Exclude
    private String definitionJson;
    private String structureHash;
    private Long revision;
    private Long connectionEpoch;
    private String catalogStatus;
    private String reviewStatus;
    private String enabled;
    private String classification;
    private String schemaSupported;
    private String schemaError;
    private Long reviewedRevision;
    private String reviewedBy;
    private Date reviewedTime;
    private String reviewNote;
    private Date lastSeenTime;
}
