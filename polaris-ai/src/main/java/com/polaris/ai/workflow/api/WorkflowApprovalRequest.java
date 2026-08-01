package com.polaris.ai.workflow.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 一次性人工审批决策。 */
@Data
public class WorkflowApprovalRequest {

    @NotNull(message = "审批决策不能为空")
    private Boolean approve;

    @Size(max = 2000, message = "审批意见长度不能超过2000")
    private String feedback;
}
