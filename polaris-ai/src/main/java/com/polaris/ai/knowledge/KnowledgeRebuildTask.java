package com.polaris.ai.knowledge;

import lombok.Data;

import java.util.Date;

/** 内部持久化实体；接口只返回脱敏 View，不返回 owner、租户、清单或私有指针。 */
@Data
public class KnowledgeRebuildTask {
    private String taskId;
    private Long tenantId;
    private Long knowledgeBaseId;
    private String requestKey;
    private String taskType = "REBUILD";
    private Long documentId;
    private Long sourceGeneration;
    private Long claimedGeneration;
    private String taskState;
    private String ownerToken;
    private Date leaseUntil;
    private boolean expired;
    private Integer totalDocuments;
    private Integer completedDocuments;
    private String errorCode;
    private String createBy;
    private Date createTime;
    private Date startTime;
    private Date finishTime;
}
