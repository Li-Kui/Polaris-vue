package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.polaris.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/** 归属于单个租户并绑定发布版本的触发器。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_workflow_trigger")
public class WorkflowTrigger extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String triggerId;
    private Long definitionId;
    private String workflowVersionId;
    private String triggerType;
    private String configJson;
    private String dedupPolicyJson;
    private String status;
    private Date nextFireTime;
    private Date lastFireTime;
    private String lastExecutionId;
    private String lastTriggerStatus;
    private String lastErrorMessage;
    @Version
    private Integer lockVersion;
}
