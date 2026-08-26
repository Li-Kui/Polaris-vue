package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.polaris.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/** 不可变的工作流发布版本。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_workflow_version")
public class WorkflowVersion extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String versionId;
    private Long tenantId;
    private Long definitionId;
    private Integer versionNo;
    private String schemaVersion;
    private String definitionJson;
    private String planSchemaVersion;
    private String executionPlanJson;
    private String contentHash;
    private String status;
    private String publishedBy;
    private Date publishedTime;
}
