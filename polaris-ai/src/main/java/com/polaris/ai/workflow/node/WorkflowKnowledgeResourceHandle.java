package com.polaris.ai.workflow.node;

import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.rag.AiVectorStoreResolver;

/** 工作流知识库资源在一次执行中的已解析句柄。 */
public record WorkflowKnowledgeResourceHandle(
        AiKnowledgeBase knowledge,
        AiVectorStoreResolver.VectorContext vectorContext) {
}
