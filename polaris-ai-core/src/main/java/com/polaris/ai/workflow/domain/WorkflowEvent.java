package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 用于审计和 SSE 重放的工作流持久化事件。 */
@Data
@TableName("ai_workflow_event")
public class WorkflowEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String executionId;
    private Long sequenceNo;
    private String eventType;
    private String nodeRunId;
    private String nodeId;
    private String payloadJson;
    private Date createTime;
}
