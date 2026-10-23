package com.polaris.platform.domain;

import lombok.Data;

/** 客服会话，仅由服务端构造；访客另绑定随机凭证摘要。 */
@Data
public class CustomerServiceSession {
    private String id;
    private Long tenantId;
    private Long appId;
    private String releaseId;
    private Long userId;
    private String visitorTokenHash;
    private Long runtimeEpoch;
    private String channelId;
    private Long channelRevision;
    private String status;
    private Integer turnCount;
    private java.util.Date createTime;
    private java.util.Date expiresAt;
}

