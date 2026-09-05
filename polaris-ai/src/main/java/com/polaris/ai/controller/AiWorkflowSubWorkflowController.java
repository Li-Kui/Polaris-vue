package com.polaris.ai.controller;

import com.polaris.ai.workflow.service.WorkflowSubWorkflowService;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@com.polaris.common.annotation.ApiGroup(com.polaris.common.constant.ApiVersionConstants.VERSION_2_0_0)
@RequestMapping("/ai/workflow/sub-workflows")
public class AiWorkflowSubWorkflowController extends BaseController {
    private final WorkflowSubWorkflowService service;
    public AiWorkflowSubWorkflowController(WorkflowSubWorkflowService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@workflowAccess.canRead()")
    public ResultData<List<WorkflowSubWorkflowService.Contract>> list(@RequestParam(required = false) Long parentId) {
        return ok(service.catalog(parentId));
    }

    @GetMapping("/{definitionId}")
    @PreAuthorize("@workflowAccess.canRead()")
    public ResultData<WorkflowSubWorkflowService.Contract> contract(@PathVariable Long definitionId,
            @RequestParam(required = false) String versionId) {
        return ok(service.resolve(definitionId, versionId, WorkflowSubWorkflowService.currentTenant()));
    }
}
