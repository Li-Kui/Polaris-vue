package com.polaris.platform.domain;

import lombok.Data;

/** 客服持久化文字轮次，仅由服务端构造。 */
@Data
public class CustomerServiceTurn {
    private String id;
    private Long tenantId;
    private String sessionId;
    private String requestId;
    private Integer turnNo;
    private String question;
    private String answer;
    private String citationsJson;
    private String answerPartsJson;
    private String intentJson;
    private String status;
    private String errorCode;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private String usageSource;
    private java.util.Date createTime;
    private java.util.Date deadline;
    private java.util.Date finishedTime;
}

