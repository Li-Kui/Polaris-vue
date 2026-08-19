package com.polaris.ai.safety.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** Bounded, sanitized candidate-rule evidence. Never stores general content fields. */
@Data
@TableName("ai_moderation_candidate")
public class ModerationCandidate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String candidateTerm;
    private String expressionHash;
    private String maskedExcerpt;
    private String category;
    private Long sourceEventId;
    private String sourceSignal;
    private Integer observationCount;
    private String status;
    private Date createTime;
    private Date updateTime;
}
