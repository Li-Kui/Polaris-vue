package com.polaris.platform.domain;

import lombok.Data;

import java.util.Date;

/** 脱敏元数据，不接受或保存参数、响应、认证信息和远端异常原文。 */
@Data
public class PlatformMcpInvocation {
    private String invocationId;
    private Long tenantId;
    private Long actorUserId;
    private String actorName;
    private String source;
    private String executionId;
    private String nodeRunId;
    private Integer attemptNo;
    private Long connectionId;
    private Long toolId;
    private Long connectionRevision;
    private Long catalogRevision;
    private Long connectionEpoch;
    private Long toolRevision;
    private String structureHash;
    private String status;
    private String errorCode;
    private String callDispatched;
    private Date startedTime;
    private Date finishedTime;
    private Long durationMs;
}
