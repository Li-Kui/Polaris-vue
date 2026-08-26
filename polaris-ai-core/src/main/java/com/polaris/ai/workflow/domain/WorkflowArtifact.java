package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 私有大体量输出产物元数据。 */
@Data
@TableName("ai_workflow_artifact")
public class WorkflowArtifact implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String artifactId;
    private String executionId;
    private String nodeRunId;
    private String fileName;
    private String storageRef;
    private String mimeType;
    private Long sizeBytes;
    private String contentHash;
    private Date expiresTime;
    private Date createTime;
}
