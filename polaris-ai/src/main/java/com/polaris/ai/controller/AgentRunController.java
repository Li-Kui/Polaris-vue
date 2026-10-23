package com.polaris.ai.controller;

import com.polaris.ai.agent.collaboration.AgentRunQueryService;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RequestMapping("/ai/agent/collaboration")
public class AgentRunController extends BaseController {
    private final AgentRunQueryService runs;
    public AgentRunController(AgentRunQueryService runs) {this.runs = runs;}
    @GetMapping("/executions/{executionId}/nodes/{nodeRunId}")
    public ResultData<AgentRunQueryService.View> get(@PathVariable String executionId, @PathVariable String nodeRunId) {
        return ok(runs.forNode(executionId, nodeRunId));
    }
    @GetMapping("/contract-impact") public ResultData<AgentRunQueryService.ImpactView> impact() {return ok(runs.impact());}
}
