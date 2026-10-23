package com.polaris.platform.domain;

import lombok.Data;

import java.util.Date;

/** 短录音识别持久化围栏，不保存音频内容。 */
@Data
public class CustomerServiceSpeech {
    private String id;
    private Long tenantId;
    private String sessionId;
    private String requestId;
    private String inputHash;
    private String kind;
    private String turnId;
    private Integer partNo;
    private Integer audioMillis;
    private String status;
    private String transcript;
    private String errorCode;
    private String usageJson;
    private Date createTime;
    private Date deadline;
    private Date finishedTime;
}
