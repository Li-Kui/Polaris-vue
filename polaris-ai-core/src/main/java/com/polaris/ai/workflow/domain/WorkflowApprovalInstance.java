package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 人工审批节点一次运行对应的审批实例。 */
@Data
@TableName("ai_workflow_approval_instance")
public class WorkflowApprovalInstance implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String approvalInstanceId;
    private String executionId;
    private String nodeRunId;
    private String configVersion;
    private String configSnapshot;
    private String contentSnapshot;
    private String resultMode;
    private String status;
    private String currentStageId;
    private Integer currentStageSequence;
    private Date deadline;
    private Date reminderTime;
    private Date reminderSentTime;
    private Integer escalationCount;
    @Version
    private Integer lockVersion;
    private Date createTime;
    private Date updateTime;
    private Date finishTime;
}
