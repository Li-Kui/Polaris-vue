package com.polaris.ai.safety.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** Persisted dictionary version metadata. */
@Data
@TableName("ai_moderation_dictionary_version")
public class ModerationDictionaryVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String versionNo;
    private String checksum;
    private String status;
    private String sourceVersion;
    private String sourceLocation;
    private String publishedBy;
    private Date publishedTime;
    private Date createTime;
    private Date updateTime;
}
