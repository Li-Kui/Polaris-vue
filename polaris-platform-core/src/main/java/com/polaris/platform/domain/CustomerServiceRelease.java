package com.polaris.platform.domain;

import lombok.Data;

/** 客服不可变发布，仅由服务端构造。 */
@Data
public class CustomerServiceRelease {
    private String id;
    private Long tenantId;
    private Long appId;
    private Long sourceRevision;
    private String configJson;
    private String modelHash;
    private String speechModelHash;
    private String playbackModelHash;
    private String createBy;
    private java.util.Date createTime;
}

