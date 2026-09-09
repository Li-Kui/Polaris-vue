package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/** 不包含业务输入输出的单节点试运行审计与费用摘要。 */
@Data
@TableName("ai_workflow_node_test_audit")
public class WorkflowNodeTestAudit implements Serializable {
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
    private String sideEffect;
    private String principalType;
    private String principalId;
    private String nodeConfigHash;
    private String schemaSource;
    private String schemaSourceVersion;
    private String outcomeStatus;
    private String usageJson;
    private Long totalTokens;
    private BigDecimal costAmount;
    private Integer attemptCount;
    private String errorCode;
    private Long durationMs;
    private Date createTime;
    private Date finishTime;
}
