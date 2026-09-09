package com.polaris.ai.workflow.spi;

import com.polaris.ai.core.context.CallerContext;

import java.util.Optional;

/** 按执行记录中的稳定主体标识实时恢复非管理端调用者。 */
public interface WorkflowPrincipalContextProvider {

    boolean supports(String principalType);

    Optional<CallerContext> resolve(
            Long tenantId, String principalType, String principalId);
}
