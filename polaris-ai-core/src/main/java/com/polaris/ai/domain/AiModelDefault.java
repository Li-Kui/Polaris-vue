package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** AI 默认模型配置。 */
@Data
@TableName("ai_model_default")
public class AiModelDefault implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String scopeType;
    private Long scopeId;
    private String capabilityCode;
    private Long modelConfigId;
    private Date createTime;
    private Date updateTime;
}
