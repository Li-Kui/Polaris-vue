package com.polaris.ai.knowledge;

import lombok.Data;

import java.util.Date;

/** 内部归属元数据，不直接作为接口响应。 */
@Data
public class KnowledgeVectorCollection {
    private String registryId;
    private Long tenantId;
    private Long knowledgeBaseId;
    private Long buildGeneration;
    private Long indexVersion;
    private String backendType;
    private String backendKey;
    private String collectionName;
    private Integer ownershipVersion;
    @lombok.ToString.Exclude
    private String ownershipMarker;
    private String registryState;
    private Boolean creationAcknowledged;
    private String errorCode;
    private Date createTime;
    private Date updateTime;
    private Date retireTime;
    private String cleanupState;
    private Integer cleanupAttempts;
    private Long cleanupRequestedBy;
    private Date cleanupRequestedAt;
    @lombok.ToString.Exclude
    private String cleanupOwnerToken;
    @lombok.ToString.Exclude
    private String cleanupPlanKey;
    private Date cleanupLeaseUntil;
    private Date cleanupAfter;
    private String cleanupError;
    private Boolean cleanupDispatched;
    private Date cleanupConfirmedAt;
}
