package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 一名审批人提交的不可变审批决定。 */
@Data
@TableName("ai_workflow_approval_decision")
public class WorkflowApprovalDecision implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String decisionId;
    private String requestId;
    private String approvalInstanceId;
    private String stageInstanceId;
    private String assignmentId;
    private String actorId;
    private String actorName;
    private String decision;
    private String comment;
    private Date createTime;
}
