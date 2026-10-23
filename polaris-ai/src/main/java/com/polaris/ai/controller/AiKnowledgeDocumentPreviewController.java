package com.polaris.ai.controller;

import com.polaris.ai.knowledge.KnowledgeDocumentPreviewService;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import org.springframework.web.bind.annotation.*;

/** 只读取已保存的实际分块；不调用模型、不重解析或隐式重建，不记录正文操作日志。 */
@RestController
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RequestMapping("/ai/knowledge/document")
public class AiKnowledgeDocumentPreviewController {
    private final KnowledgeDocumentPreviewService service;
    public AiKnowledgeDocumentPreviewController(KnowledgeDocumentPreviewService service) {this.service = service;}
    @GetMapping("/{id}/snapshot")
    public ResultData<KnowledgeDocumentPreviewService.Preview> snapshot(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return ResultData.ok(service.preview(id, page, pageSize));
    }
}
