package com.polaris.platform.domain;

import lombok.Data;

import java.util.Date;

/** 独立的客服发布渠道；支持管理员登录及显式启用的访客知识咨询。 */
@Data
public class CustomerServiceChannel {
    private String id;
    private Long tenantId;
    private Long appId;
    private String releaseId;
    private String code;
    private String name;
    private String accessMode;
    private Integer visitorDailySessions;
    private Integer visitorMessageLimit;
    private Boolean visitorSpeechEnabled;
    private Integer visitorSpeechLimit;
    private String allowedOriginsJson;
    private String capabilityCodesJson;
    private String status;
    private Long revision;
    private Date expiresAt;
    private String createBy;
    private Date createTime;
    private Date updateTime;
}
