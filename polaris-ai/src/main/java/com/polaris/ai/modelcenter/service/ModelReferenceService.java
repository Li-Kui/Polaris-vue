package com.polaris.ai.modelcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.ai.domain.*;
import com.polaris.ai.mapper.*;
import com.polaris.ai.workflow.domain.WorkflowResourceBinding;
import com.polaris.ai.workflow.mapper.WorkflowResourceBindingMapper;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 模型删除前的外部业务引用检查。 */
@Service
@Transactional(readOnly = true)
public class ModelReferenceService {

    private final AiAgentMapper agentMapper;
    private final AiKnowledgeMapper knowledgeMapper;
    private final AiChatMapper chatMapper;
    private final AiImageTaskMapper imageTaskMapper;
    private final WorkflowResourceBindingMapper workflowBindingMapper;
    private final AiModelDefaultMapper defaultMapper;
    private final ModelAggregateAccessGuard accessGuard;

    public ModelReferenceService(
            AiAgentMapper agentMapper,
            AiKnowledgeMapper knowledgeMapper,
            AiChatMapper chatMapper,
            AiImageTaskMapper imageTaskMapper,
            WorkflowResourceBindingMapper workflowBindingMapper,
            AiModelDefaultMapper defaultMapper,
            ModelAggregateAccessGuard accessGuard) {
        this.agentMapper = agentMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.chatMapper = chatMapper;
        this.imageTaskMapper = imageTaskMapper;
        this.workflowBindingMapper = workflowBindingMapper;
        this.defaultMapper = defaultMapper;
        this.accessGuard = accessGuard;
    }

    public ModelReferenceSummary inspect(Long modelConfigId) {
        Long id = Objects.requireNonNull(modelConfigId, "modelConfigId");
        accessGuard.requireAccessible(id);
        long agents = agentMapper.selectCount(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getModelConfigId, id));
        long knowledgeBases = knowledgeMapper.selectCount(
                new LambdaQueryWrapper<AiKnowledgeBase>()
                        .eq(AiKnowledgeBase::getEmbeddingModelId, id));
        long conversations = chatMapper.selectCount(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getModelConfigId, id)
                .eq(AiConversation::getDelFlag, "0"));
        long imageTasks = imageTaskMapper.selectCount(new LambdaQueryWrapper<AiImageTask>()
                .eq(AiImageTask::getModelConfigId, id));
        long workflowBindings = workflowBindingMapper.selectCount(
                new LambdaQueryWrapper<WorkflowResourceBinding>()
                        .eq(WorkflowResourceBinding::getResourceKind, "MODEL")
                        .eq(WorkflowResourceBinding::getResourceId, id.toString())
                        .eq(WorkflowResourceBinding::getStatus, "ACTIVE"));
        long defaults = defaultMapper.selectCount(new LambdaQueryWrapper<AiModelDefault>()
                .eq(AiModelDefault::getModelConfigId, id));
        return new ModelReferenceSummary(
                agents, knowledgeBases, conversations, imageTasks,
                workflowBindings, defaults);
    }

    public void assertNotReferenced(Long modelConfigId) {
        ModelReferenceSummary summary = inspect(modelConfigId);
        if (summary.hasReferences()) {
            throw new ServiceException("模型仍被业务引用，不能删除，引用数：" + summary.total());
        }
    }
}
