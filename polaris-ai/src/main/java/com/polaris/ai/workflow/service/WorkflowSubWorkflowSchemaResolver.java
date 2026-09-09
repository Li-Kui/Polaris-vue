package com.polaris.ai.workflow.service;

import com.polaris.ai.workflow.spi.ResolvedNodeSchema;
import com.polaris.ai.workflow.spi.WorkflowNodeSchemaContext;
import com.polaris.ai.workflow.spi.WorkflowNodeSchemaResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class WorkflowSubWorkflowSchemaResolver implements WorkflowNodeSchemaResolver {
    private final WorkflowSubWorkflowService service;
    public WorkflowSubWorkflowSchemaResolver(WorkflowSubWorkflowService service) { this.service = service; }
    @Override public boolean supports(String type, String version) { return "sub_workflow".equals(type) && "2.0".equals(version); }
    @Override public ResolvedNodeSchema resolve(WorkflowNodeSchemaContext context) {
        var contract = service.resolve(context.config().path("definitionId").asLong(),
                context.config().path("reviewedVersionId").asText(), context.tenantId());
        return new ResolvedNodeSchema(contract.inputSchema(), service.envelope(contract.outputSchema()),
                "SUB_WORKFLOW_VERSION", contract.versionId(), Map.of(), List.of());
    }
}
