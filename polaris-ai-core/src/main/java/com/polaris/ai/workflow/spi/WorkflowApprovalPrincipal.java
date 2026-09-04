package com.polaris.ai.workflow.spi;

import java.util.List;

/** 审批目标解析后得到的具体有效用户。 */
public record WorkflowApprovalPrincipal(
        String userId,
        String username,
        String displayName,
        String departmentId,
        String departmentName,
        List<String> sources) {
}
