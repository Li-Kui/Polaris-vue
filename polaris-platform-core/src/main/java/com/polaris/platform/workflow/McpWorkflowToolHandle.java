package com.polaris.platform.workflow;

import com.polaris.ai.workflow.contract.WorkflowMcpNodeContract;
import tools.jackson.databind.node.ObjectNode;

/** 只含本地身份与修订，不缓存连接对象或凭据。 */
public record McpWorkflowToolHandle(Long toolId, Long connectionId, long revision,
        long connectionRevision, long catalogRevision, String structureHash) {
    public ObjectNode snapshot() {
        return WorkflowMcpNodeContract.snapshot(String.valueOf(toolId), revision,
                connectionRevision, catalogRevision, structureHash);
    }
}
