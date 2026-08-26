package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 包含不可变审批人策略快照的审批任务。 */
@Data
@TableName("ai_workflow_approval_task")
public class WorkflowApprovalTask implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String approvalTaskId;
    private String executionId;
    private String nodeRunId;
    private String assigneeType;
    private String assigneeSnapshot;
    private String approvalMode;
    private Integer requiredApprovals;
    private Boolean allowSelfApproval;
    private String status;
    private String decisionSummary;
    private Date deadline;
    @Version
    private Integer lockVersion;
    private Date createTime;
    private Date updateTime;
    private Date finishTime;
}
