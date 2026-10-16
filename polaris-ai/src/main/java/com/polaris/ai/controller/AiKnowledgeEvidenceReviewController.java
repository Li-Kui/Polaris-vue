package com.polaris.ai.controller;

import com.polaris.ai.knowledge.KnowledgeEvidenceReviewService;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import org.springframework.web.bind.annotation.*;

/** 显式确认才调用审阅模型；正文不进入操作日志，不保存候选建议，不接入普通聊天。 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RestController
@RequestMapping("/ai/chat/conversations")
public class AiKnowledgeEvidenceReviewController {
    private final KnowledgeEvidenceReviewService service;
    public AiKnowledgeEvidenceReviewController(KnowledgeEvidenceReviewService service) {this.service=service;}
    @PostMapping("/{conversationId}/messages/{messageId}/evidence-review")
    public ResultData<?> review(@PathVariable Long conversationId,
            @PathVariable Long messageId,@RequestBody KnowledgeEvidenceReviewService.Request request) {
        try {return ResultData.ok(service.review(conversationId,messageId,request));}
        catch(KnowledgeEvidenceReviewService.ReviewFailure failure) {
            return ResultData.fail(failure.response(),failure.getCode(),failure.getMessage());
        }
    }
}
