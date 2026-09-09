package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 单节点隔离试运行的持久化状态与脱敏结果。 */
@Data
@TableName("ai_workflow_node_test_run")
public class WorkflowNodeTestRun implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String testRunId;
    private Long definitionId;
    private Long draftRevision;
    private String nodeId;
    private String nodeType;
    private String handlerVersion;
    private String environment;
    private String testMode;
    private String status;
    private String sideEffect;
    private String principalType;
    private String principalId;
    private String inputJson;
    private String payloadCiphertext;
    private String outputJson;
    private String usageJson;
    private String schemaSource;
    private String schemaSourceVersion;
    private String schemaDiagnosticsJson;
    private String nodeConfigHash;
    private Integer timeoutSeconds;
    private Boolean cancelRequested;
    private String runnerId;
    private Date leaseUntil;
    private Long fencingToken;
    private Integer attemptCount;
    private String errorCode;
    private String errorMessage;
    private Long durationMs;
    private Date createTime;
    private Date updateTime;
    private Date startTime;
    private Date heartbeatTime;
    private Date finishTime;
}
