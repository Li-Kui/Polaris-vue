package com.polaris.ai.workflow.spi;

import java.util.Optional;

/** 为 AI 工作流提供受租户边界约束的中台只读数据。 */
public interface WorkflowPlatformDataProvider {

    /** 仅根据当前 CallerContext 查询当前租户，不接受外部租户标识。 */
    Optional<TenantUsageSummary> currentTenantUsage();

    /** 不包含联系人、手机号等个人信息的租户用量概览。 */
    record TenantUsageSummary(
            Long tenantId,
            String tenantName,
            String tenantCode,
            Long quotaTokens,
            Long usedTokens) {
    }
}
