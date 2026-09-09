package com.polaris.ai.safety.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/** Complete persisted runtime policy for one moderation scene. */
@Data
@TableName("ai_moderation_policy")
public class ModerationPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String scene;
    private String preset;
    private String mode;
    private Boolean enabled;
    private Integer suspectThreshold;
    private Integer blockThreshold;
    private Boolean providerEnabled;
    private Integer providerTimeoutMs;
    private Integer providerDailyLimit;
    private BigDecimal providerMonthlyBudget;
    private Integer segmentChars;
    private Integer segmentOverlapChars;
    private Integer outputBufferChars;
    private Integer quarantineDays;
    private Long policyVersion;
    private Date createTime;
    private Date updateTime;
}
