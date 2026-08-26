package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 绑定不可变执行计划摘要的安全恢复检查点。 */
@Data
@TableName("ai_workflow_checkpoint")
public class WorkflowCheckpoint implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String executionId;
    private Long sequenceNo;
    private String planHash;
    private String nodeRunId;
    private String stateJson;
    private String status;
    private Date createTime;
}
