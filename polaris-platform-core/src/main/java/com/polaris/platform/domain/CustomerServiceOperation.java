package com.polaris.platform.domain;

import lombok.Data;

/** 客服请求与工作流持久化执行的不可变关联，不复制原始执行结果。 */
@Data
public class CustomerServiceOperation {
    private String id;
    private Long tenantId;
    private String sessionId;
    private String requestId;
    private String capabilityCode;
    private Long definitionId;
    private String workflowVersionId;
    private String executionId;
    private String inputJson;
    private java.util.Date createTime;
}
