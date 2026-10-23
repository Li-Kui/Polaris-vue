package com.polaris.ai.controller;

import com.polaris.ai.knowledge.KnowledgeRetrievalTestService;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

/** 权限校验在服务内执行，诊断 POST 不记入带请求正文的操作日志。 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RestController
@RequestMapping("/ai/knowledge")
public class AiKnowledgeRetrievalTestController {
    private final KnowledgeRetrievalTestService service;

    public AiKnowledgeRetrievalTestController(KnowledgeRetrievalTestService service) { this.service = service; }

    @org.springframework.web.bind.annotation.GetMapping("/{knowledgeId}/retrieval-capabilities")
    public ResultData<java.util.Map<String, Object>> capabilities(@PathVariable Long knowledgeId) {
        return ResultData.ok(service.capabilities(knowledgeId));
    }

    @Operation(summary = "只检索测试：使用当前向量模型，不生成回答或保存问题")
    @PostMapping("/{knowledgeId}/retrieval-test")
    public ResultData<KnowledgeRetrievalTestService.TestResponse> test(
            @PathVariable Long knowledgeId, @RequestBody KnowledgeRetrievalTestService.TestRequest request) {
        return ResultData.ok(service.test(knowledgeId, request));
    }
}
