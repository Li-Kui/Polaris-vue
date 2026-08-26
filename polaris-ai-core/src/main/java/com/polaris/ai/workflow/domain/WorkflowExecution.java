package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 工作流持久化执行记录。 */
@Data
@TableName("ai_workflow_execution")
public class WorkflowExecution implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String executionId;
    private String parentExecutionId;
    private String rootExecutionId;
    private Integer executionDepth;
    private Long definitionId;
    private String workflowVersionId;
    private String workflowCode;
    private Integer versionNo;
    private String planHash;
    private String principalType;
    private String principalId;
    private String idempotencyScope;
    private String idempotencyKey;
    private String principalSnapshot;
    private String bindingSnapshot;
    private String environment;
    private String inputJson;
    private String outputJson;
    private String status;
    private String runnerId;
    private Long fencingToken;
    private Date leaseUntil;
    private Date resumeTime;
    private Date heartbeatTime;
    private Integer recoveryCount;
    private Boolean cancelRequested;
    private String budgetJson;
    private String usageJson;
    private String quotaScopesJson;
    private Boolean quotaReleased;
    private String errorCode;
    private String errorMessage;
    private Long eventSequence;
    private Long checkpointSequence;
    @Version
    private Integer lockVersion;
    private Date createTime;
    private Date updateTime;
    private Date startTime;
    private Date finishTime;
}
