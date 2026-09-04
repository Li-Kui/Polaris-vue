package com.polaris.ai.workflow.spi;

import java.util.List;

/** 一个审批级别中的人员、角色或部门目标。 */
public record WorkflowApprovalTarget(
        String type,
        List<String> ids,
        boolean includeChildren) {
}
