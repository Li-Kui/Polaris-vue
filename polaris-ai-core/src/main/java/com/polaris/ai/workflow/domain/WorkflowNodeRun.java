package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 稳定逻辑节点运行的一次持久化尝试记录。 */
@Data
@TableName("ai_workflow_node_run")
public class WorkflowNodeRun implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String nodeRunId;
    private String executionId;
    private String nodeId;
    private String branchPath;
    private Integer attemptNo;
    private String handlerVersion;
    private String status;
    private String sideEffect;
    private String sideEffectStatus;
    private String idempotencyKey;
    private String inputJson;
    private String outputJson;
    private String errorCode;
    private String errorMessage;
    private Long fencingToken;
    private Date createTime;
    private Date updateTime;
    private Date startTime;
    private Date finishTime;
}
