package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.polaris.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 工作流定义及可修改草稿。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_workflow_definition")
public class WorkflowDefinition extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String ownerType;
    private Long ownerId;
    private String workflowCode;
    private String workflowName;
    private String description;
    private String tagsJson;
    private String draftSchemaVersion;
    private String draftJson;
    private Long draftRevision;
    private String currentPublishedVersionId;
    private String status;
    @Version
    private Integer lockVersion;
    @TableLogic(value = "0", delval = "2")
    private String delFlag;
}
