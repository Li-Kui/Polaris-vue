package com.polaris.ai.safety.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** Sanitized audit event; categories are stored as comma-separated stable codes. */
@Data
@TableName("ai_moderation_event")
public class ModerationEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private String scene;
    private String resourceType;
    private String resourceId;
    private Long dictionaryVersion;
    private Long policyVersion;
    private String localDecision;
    private String finalAction;
    private Integer riskScore;
    private String categories;
    private String matchedRuleIds;
    private String provider;
    private String providerRequestId;
    private String providerDecision;
    private Long providerLatencyMs;
    private String fallbackReason;
    private String contentHash;
    private String maskedExcerpt;
    private Date expireTime;
    private Date createTime;
}
