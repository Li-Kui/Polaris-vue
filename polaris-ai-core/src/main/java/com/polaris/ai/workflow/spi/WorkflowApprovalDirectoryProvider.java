package com.polaris.ai.workflow.spi;

import java.util.List;

/** 按管理端或租户端身份目录把审批目标解析为具体用户。 */
public interface WorkflowApprovalDirectoryProvider {

    /** 当前实现能否处理指定租户范围，系统管理端的 tenantId 为 null。 */
    boolean supports(Long tenantId);

    /** 解析并返回有效用户，调用方会再次按 userId 去重和执行人数上限校验。 */
    List<WorkflowApprovalPrincipal> resolve(
            Long tenantId, List<WorkflowApprovalTarget> targets);

    /** 返回当前操作范围可在审批编辑器中选择的目录项。 */
    List<WorkflowApprovalDirectoryEntry> list(Long tenantId, String keyword);
}
