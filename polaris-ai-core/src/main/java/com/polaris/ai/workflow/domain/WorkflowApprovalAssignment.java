package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 审批级别激活时固化的一名具体用户审批资格。 */
@Data
@TableName("ai_workflow_approval_assignment")
public class WorkflowApprovalAssignment implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String assignmentId;
    private String approvalInstanceId;
    private String stageInstanceId;
    private String userId;
    private String username;
    private String displayName;
    private String departmentId;
    private String departmentName;
    private String sourceSnapshot;
    private String status;
    private Date createTime;
    private Date updateTime;
}
