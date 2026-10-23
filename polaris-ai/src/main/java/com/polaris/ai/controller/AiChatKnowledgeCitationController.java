package com.polaris.ai.controller;

import com.polaris.ai.knowledge.ChatKnowledgeEvidenceService;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 不记录正文操作日志；每次读取重新鉴权，不调用模型或重建索引。 */
@RestController
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RequestMapping("/ai/chat/conversations")
public class AiChatKnowledgeCitationController {
    private final ChatKnowledgeEvidenceService service;
    public AiChatKnowledgeCitationController(ChatKnowledgeEvidenceService service) {this.service=service;}
    @GetMapping("/{conversationId}/messages/{messageId}/citations/{citationId}")
    public ResultData<ChatKnowledgeEvidenceService.Preview> preview(@PathVariable Long conversationId,
            @PathVariable Long messageId, @PathVariable String citationId) {
        return ResultData.ok(service.preview(conversationId,messageId,citationId));
    }
}
