package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** AI 模型能力配置。 */
@Data
@TableName("ai_model_capability")
public class AiModelCapability implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modelConfigId;
    private String capabilityCode;
    private String appliesToCapabilityCode;
    private Integer schemaVersion;
    private String schemaHash;
    private String configJson;
    private String enabled;
    private String capabilitySource;
    private Date createTime;
    private Date updateTime;
}
