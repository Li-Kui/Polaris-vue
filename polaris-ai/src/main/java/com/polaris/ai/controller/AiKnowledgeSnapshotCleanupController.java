package com.polaris.ai.controller;

import com.polaris.ai.knowledge.KnowledgeSnapshotCleanupService;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import org.springframework.web.bind.annotation.*;

@RestController
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RequestMapping("/ai/knowledge/{knowledgeId}/snapshot-cleanup")
public class AiKnowledgeSnapshotCleanupController {
    private final KnowledgeSnapshotCleanupService cleanup;
    public AiKnowledgeSnapshotCleanupController(KnowledgeSnapshotCleanupService cleanup) {this.cleanup=cleanup;}
    @PostMapping("/preview")
    public ResultData<KnowledgeSnapshotCleanupService.Preview> preview(@PathVariable Long knowledgeId) {return ResultData.ok(cleanup.preview(knowledgeId));}
    @GetMapping("/status")
    public ResultData<KnowledgeSnapshotCleanupService.StatusPage> status(@PathVariable Long knowledgeId,@RequestParam(required=false) String beforeId) {
        return ResultData.ok(cleanup.statusFromRequest(knowledgeId,beforeId));
    }
    @PostMapping("/execute")
    public ResultData<KnowledgeSnapshotCleanupService.Result> execute(@PathVariable Long knowledgeId,
            @RequestBody KnowledgeSnapshotCleanupService.Request request) {return ResultData.ok(cleanup.execute(knowledgeId,request));}
}
