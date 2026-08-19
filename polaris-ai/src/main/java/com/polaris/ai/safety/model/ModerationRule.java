package com.polaris.ai.safety.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** Persisted rule belonging to one dictionary version. */
@Data
@TableName("ai_moderation_rule")
public class ModerationRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dictionaryVersionId;
    private String ruleType;
    private String content;
    private String normalizedContent;
    private String normalizedHash;
    private String category;
    private Integer weight;
    private String source;
    private Date createTime;
    private Date updateTime;
}
