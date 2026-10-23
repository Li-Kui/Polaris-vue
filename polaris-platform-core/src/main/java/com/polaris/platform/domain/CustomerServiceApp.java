package com.polaris.platform.domain;

import lombok.Data;

/** 客服应用草稿，仅由服务端构造。 */
@Data
public class CustomerServiceApp {
    private Long id;
    private Long tenantId;
    private String name;
    private String configJson;
    private Long revision;
    private Long runtimeEpoch;
    private String status;
    private String currentReleaseId;
    private String createBy;
    private java.util.Date createTime;
    private java.util.Date updateTime;
}

