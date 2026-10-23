package com.polaris.platform.domain;

import lombok.Data;

import java.util.Date;

/** 人物素材不可变元数据；物理文件位于私有存储，客户端只持有随机 ID。 */
@Data
public class CustomerServiceAvatarAsset {
    private String id;
    private Long tenantId;
    private String name;
    private String kind;
    private String contentType;
    private Long byteSize;
    private String contentHash;
    private Long createBy;
    private Date createTime;
}
