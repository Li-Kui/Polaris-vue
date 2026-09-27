package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/** AI 模型运行策略。 */
@Data
@TableName("ai_model_runtime_policy")
public class AiModelRuntimePolicy implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modelConfigId;
    private String capabilityCode;
    private Integer maxConcurrency;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Integer retryCount;
    private BigDecimal qpsLimit;
    private Integer priority;
    private String extraConfig;
    private Date createTime;
    private Date updateTime;
}
