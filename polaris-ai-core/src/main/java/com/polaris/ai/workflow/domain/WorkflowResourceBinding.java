package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.polaris.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 按所有者和环境隔离的逻辑资源绑定。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_workflow_resource_binding")
public class WorkflowResourceBinding extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ownerType;
    private Long ownerId;
    private Long tenantId;
    private String environment;
    private String resourceKind;
    private String resourceKey;
    private String resourceId;
    private Integer bindingVersion;
    private String status;
    @Version
    private Integer lockVersion;
}
