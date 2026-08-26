package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 用于可靠投递的事务发件箱记录。 */
@Data
@TableName("ai_workflow_outbox")
public class WorkflowOutbox implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String eventId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payloadJson;
    private String publishStatus;
    private Integer attemptCount;
    private String claimedBy;
    private Date claimUntil;
    private Date nextRetryTime;
    private Date publishedTime;
    private Date createTime;
    private Date updateTime;
}
