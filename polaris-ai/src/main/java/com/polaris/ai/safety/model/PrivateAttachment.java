package com.polaris.ai.safety.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** Private attachment staging record, retained only until its cleanup deadline. */
@Data
@TableName("ai_private_attachment")
public class PrivateAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String attachmentToken;
    private Long userId;
    private String originalName;
    private String storagePath;
    private String status;
    private Date expireTime;
    private Date createTime;
    private Date updateTime;
}
