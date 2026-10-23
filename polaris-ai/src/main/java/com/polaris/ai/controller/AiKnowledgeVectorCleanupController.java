package com.polaris.ai.controller;

import com.polaris.ai.knowledge.KnowledgeVectorCleanupService;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import org.springframework.web.bind.annotation.*;

@RestController
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RequestMapping("/ai/knowledge/{knowledgeId}/vector-collections/{buildGeneration}/cleanup")
public class AiKnowledgeVectorCleanupController {
    private final KnowledgeVectorCleanupService cleanup;
    public AiKnowledgeVectorCleanupController(KnowledgeVectorCleanupService cleanup) {this.cleanup=cleanup;}
    @PostMapping("/plan")
    public ResultData<KnowledgeVectorCleanupService.Plan> plan(@PathVariable Long knowledgeId,@PathVariable Long buildGeneration) {return ResultData.ok(cleanup.plan(knowledgeId,buildGeneration));}
    @GetMapping
    public ResultData<KnowledgeVectorCleanupService.View> status(@PathVariable Long knowledgeId,@PathVariable Long buildGeneration) {return ResultData.ok(cleanup.status(knowledgeId,buildGeneration));}
    @PostMapping("/execute")
    public ResultData<KnowledgeVectorCleanupService.View> execute(@PathVariable Long knowledgeId,@PathVariable Long buildGeneration,@RequestBody(required=false) KnowledgeVectorCleanupService.Request request) {return ResultData.ok(cleanup.execute(knowledgeId,buildGeneration,request));}
    @PostMapping("/stop-expired")
    public ResultData<KnowledgeVectorCleanupService.View> stop(@PathVariable Long knowledgeId,@PathVariable Long buildGeneration,@RequestBody(required=false) KnowledgeVectorCleanupService.StopRequest request) {return ResultData.ok(cleanup.stopExpired(knowledgeId,buildGeneration,request));}
}
