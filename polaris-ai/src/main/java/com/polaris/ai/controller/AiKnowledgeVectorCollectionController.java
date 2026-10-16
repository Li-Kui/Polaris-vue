package com.polaris.ai.controller;

import com.polaris.ai.knowledge.KnowledgeVectorCollectionRegistry;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RequestMapping("/ai/knowledge/{knowledgeId}/vector-collections")
public class AiKnowledgeVectorCollectionController {
    private final KnowledgeVectorCollectionRegistry registry;
    public AiKnowledgeVectorCollectionController(KnowledgeVectorCollectionRegistry registry) {this.registry=registry;}
    @GetMapping
    public ResultData<List<KnowledgeVectorCollectionRegistry.View>> history(@PathVariable Long knowledgeId) {
        return ResultData.ok(registry.history(knowledgeId));
    }
    @GetMapping("/cleanup-preview")
    public ResultData<KnowledgeVectorCollectionRegistry.CleanupPreview> preview(@PathVariable Long knowledgeId) {
        return ResultData.ok(registry.cleanupPreview(knowledgeId));
    }
    @PostMapping("/{buildGeneration}/verify-ownership")
    public ResultData<KnowledgeVectorCollectionRegistry.Verification> verify(@PathVariable Long knowledgeId,@PathVariable Long buildGeneration) {
        return ResultData.ok(registry.verifyOwnership(knowledgeId,buildGeneration));
    }
}
