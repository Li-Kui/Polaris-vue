package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 单个并发范围的原子活跃执行计数器。 */
@Data
@TableName("ai_workflow_concurrency_quota")
public class WorkflowConcurrencyQuota implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String scopeKey;
    private String scopeType;
    private Integer maxActive;
    private Integer activeCount;
    private Date createTime;
    private Date updateTime;
}
