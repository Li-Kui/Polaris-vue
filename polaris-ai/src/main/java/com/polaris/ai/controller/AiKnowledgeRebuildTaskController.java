package com.polaris.ai.controller;

import com.polaris.ai.knowledge.KnowledgeRebuildTaskService;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import org.springframework.web.bind.annotation.*;

@RestController
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RequestMapping("/ai/knowledge/{knowledgeId}/rebuild-tasks")
public class AiKnowledgeRebuildTaskController {
    private final KnowledgeRebuildTaskService service;
    public AiKnowledgeRebuildTaskController(KnowledgeRebuildTaskService service) {this.service = service;}

    @GetMapping("/latest")
    public ResultData<KnowledgeRebuildTaskService.View> latest(@PathVariable Long knowledgeId) {
        return ResultData.ok(service.latest(knowledgeId));
    }
    @GetMapping("/{taskId}")
    public ResultData<KnowledgeRebuildTaskService.View> get(@PathVariable Long knowledgeId, @PathVariable String taskId) {
        return ResultData.ok(service.get(knowledgeId, taskId));
    }
    @PostMapping("/{taskId}/recover")
    public ResultData<KnowledgeRebuildTaskService.View> recover(@PathVariable Long knowledgeId, @PathVariable String taskId,
            @RequestHeader(value = "Idempotency-Key", required = false) String requestKey) {
        return ResultData.ok(service.submit(knowledgeId, requestKey, taskId));
    }
}
