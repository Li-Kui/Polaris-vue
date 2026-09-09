package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 审批实例中按顺序激活的一个审批级别。 */
@Data
@TableName("ai_workflow_approval_stage")
public class WorkflowApprovalStage implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String stageInstanceId;
    private String approvalInstanceId;
    private String stageKey;
    private Integer sequenceNo;
    private String stageName;
    private String policySnapshot;
    private String status;
    private Integer requiredApprovals;
    private Integer approvedCount;
    private Integer rejectedCount;
    private Integer pendingCount;
    private Date deadline;
    @Version
    private Integer lockVersion;
    private Date createTime;
    private Date updateTime;
    private Date finishTime;
}
