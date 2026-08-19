package com.polaris.ai.workflow.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.domain.AiAgent;
import com.polaris.ai.domain.AiConversation;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.domain.AiWorkflow;
import com.polaris.ai.mapper.AiChatMapper;
import com.polaris.ai.pivot.AiModelFactory;
import com.polaris.ai.service.IAiAgentService;
import com.polaris.ai.service.IAiWorkflowService;
import com.polaris.ai.tools.SecurityContextToolExecutor;
import com.polaris.ai.tools.base.AiTool;
import com.polaris.ai.workflow.WorkflowNodeExecutor;
import com.polaris.ai.workflow.langgraph.GraphTopology;
import com.polaris.common.exception.ServiceException;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 校验请求并创建不可变的工作流执行快照。 */
@Service
public class WorkflowExecutionService {

    private final IAiWorkflowService workflowService;
    private final AiChatMapper aiChatMapper;
    private final WorkflowExecutionStore executionStore;
    private final ObjectMapper objectMapper;
    private final IAiAgentService agentService;
    private final AiModelFactory modelFactory;
    private List<WorkflowNodeExecutor> javaExecutors = List.of();
    private List<AiTool> allTools = List.of();
    private final int maxConcurrentPerUser;
    private final int maxPendingApprovalsPerUser;

    @Autowired(required = false)
    private com.polaris.ai.safety.guard.IModeratedInputPreparationService moderatedInputPreparationService;

    public void setModeratedInputPreparationService(com.polaris.ai.safety.guard.IModeratedInputPreparationService moderatedInputPreparationService) {
        this.moderatedInputPreparationService = moderatedInputPreparationService;
    }

    public WorkflowExecutionService(
            IAiWorkflowService workflowService,
            AiChatMapper aiChatMapper,
            WorkflowExecutionStore executionStore,
            ObjectMapper objectMapper,
            IAiAgentService agentService,
            AiModelFactory modelFactory,
            @Value("${ai.workflow.max-concurrent-per-user:3}") int maxConcurrentPerUser,
            @Value("${ai.workflow.max-pending-approvals-per-user:20}")
            int maxPendingApprovalsPerUser) {
        this.workflowService = workflowService;
        this.aiChatMapper = aiChatMapper;
        this.executionStore = executionStore;
        this.objectMapper = objectMapper;
        this.agentService = agentService;
        this.modelFactory = modelFactory;
        this.maxConcurrentPerUser = Math.max(1, maxConcurrentPerUser);
        this.maxPendingApprovalsPerUser = Math.max(1, maxPendingApprovalsPerUser);
    }

    @Autowired(required = false)
    void setJavaExecutors(List<WorkflowNodeExecutor> javaExecutors) {
        this.javaExecutors = javaExecutors == null ? List.of() : javaExecutors;
    }

    @Autowired(required = false)
    void setAllTools(List<AiTool> allTools) {
        this.allTools = allTools == null ? List.of() : allTools;
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionStore.Execution prepare(com.polaris.ai.workflow.api.WorkflowRunRequest request, Long userId) {
        if (request == null) {
            throw new ServiceException("请求参数不能为空");
        }
        return prepare(request.getWorkflowCode(), request.getMessage(), request.getFileUrl(),
                request.getAttachmentTokens(), request.getConversationId(), userId, request.isTestRun());
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionStore.Execution prepare(
            String workflowCode, String input, String fileUrl,
            Long conversationId, Long userId, boolean testRun) {
        return prepare(workflowCode, input, fileUrl, java.util.List.of(), conversationId, userId, testRun);
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionStore.Execution prepare(
            String workflowCode, String input, String fileUrl,
            List<String> attachmentTokens,
            Long conversationId, Long userId, boolean testRun) {

        if (moderatedInputPreparationService != null) {
            moderatedInputPreparationService.prepare(
                    com.polaris.ai.safety.model.ModerationScene.WORKFLOW_INPUT,
                    input,
                    attachmentTokens,
                    userId,
                    "WORKFLOW",
                    workflowCode
            );
        }

        if (!executionStore.lockUserForExecution(userId)) {
            throw new ServiceException("当前用户不存在或已停用");
        }
        if (executionStore.countActiveByUser(userId) >= maxConcurrentPerUser) {
            throw new ServiceException("当前用户运行中的工作流过多，请等待已有任务完成");
        }
        if (executionStore.countPendingApprovalsByUser(userId) >= maxPendingApprovalsPerUser) {
            throw new ServiceException("当前用户待审批的工作流过多，请先处理已有审批任务");
        }

        AiWorkflow workflow = workflowService.selectWorkflowByCode(workflowCode);
        if (workflow == null) {
            throw new ServiceException("工作流不存在或未启用");
        }
        if (workflow.getGraphJson() == null || workflow.getGraphJson().isBlank()) {
            throw new ServiceException("工作流尚未配置图拓扑");
        }
        String immutableSnapshot = createSnapshot(workflow.getGraphJson());

        Long ownedConversationId = testRun ? null : conversationId;
        if (ownedConversationId != null) {
            AiConversation conversation = aiChatMapper.selectConversationById(ownedConversationId, userId);
            if (conversation == null) {
                throw new ServiceException("会话不存在或无权访问");
            }
        }

        WorkflowExecutionStore.Execution execution = new WorkflowExecutionStore.Execution(
                UUID.randomUUID().toString(),
                workflow.getWorkflowCode(),
                workflow.getVersion() == null ? 1 : workflow.getVersion(),
                immutableSnapshot,
                userId,
                ownedConversationId,
                testRun,
                input,
                fileUrl,
                WorkflowExecutionStore.QUEUED);
        executionStore.create(execution);
        return execution;
    }

    public WorkflowExecutionStore.Execution getOwned(String executionId, Long userId) {
        return executionStore.findOwned(executionId, userId)
                .orElseThrow(() -> new ServiceException("工作流执行不存在或无权访问"));
    }

    private String createSnapshot(String graphJson) {
        try {
            GraphTopology topology = objectMapper.readValue(graphJson, GraphTopology.class);
            topology.validate();
            WorkflowExecutionSnapshot snapshot = new WorkflowExecutionSnapshot();
            snapshot.setTopology(topology);

            AiModelConfig defaultModel = null;
            for (GraphTopology.NodeDef node : topology.getNodes()) {
                if ("agent".equals(node.getType())) {
                    AiAgent agent = agentService.selectAgentByCode(node.getRef());
                    if (agent == null) {
                        throw new ServiceException("工作流引用的智能体不存在或未启用: " + node.getRef());
                    }
                    Long modelId = agent.getModelConfigId();
                    if (modelId == null) {
                        if (defaultModel == null) {
                            defaultModel = modelFactory.getDefaultChatModelConfig();
                        }
                        modelId = requireModel(defaultModel, "智能体 " + node.getRef()).getId();
                    }
                    snapshot.getAgents().put(
                            node.getRef(), WorkflowExecutionSnapshot.AgentSnapshot.from(agent, modelId));
                    addModelSnapshot(snapshot, modelId);
                    addToolSnapshots(snapshot, agent);
                } else if ("classifier".equals(node.getType())) {
                    Long modelId = node.getModelConfigId();
                    if (modelId == null) {
                        if (defaultModel == null) {
                            defaultModel = modelFactory.getDefaultChatModelConfig();
                        }
                        modelId = requireModel(defaultModel, "分类节点 " + node.getId()).getId();
                        node.setModelConfigId(modelId);
                    }
                    addModelSnapshot(snapshot, modelId);
                } else if ("java".equals(node.getType())) {
                    WorkflowNodeExecutor executor = javaExecutors.stream()
                            .filter(candidate -> node.getRef().equals(candidate.getNodeCode()))
                            .findFirst()
                            .orElseThrow(() -> new ServiceException(
                                    "工作流引用的 Java 节点执行器不存在: " + node.getRef()));
                    snapshot.getJavaExecutors().put(node.getRef(),
                            WorkflowExecutionSnapshot.ImplementationSnapshot.from(executor));
                }
            }
            return objectMapper.writeValueAsString(snapshot);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(e.getMessage());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("工作流执行快照创建失败: " + e.getMessage());
        }
    }

    private void addToolSnapshots(
            WorkflowExecutionSnapshot snapshot, AiAgent agent) {
        for (String toolClassName :
                SecurityContextToolExecutor.resolveToolClassNames(agent.getTools())) {
            AiTool tool = allTools.stream()
                    .filter(candidate -> toolClassName.equals(
                            AopUtils.getTargetClass(candidate).getSimpleName()))
                    .findFirst()
                    .orElseThrow(() -> new ServiceException(
                            "智能体 " + agent.getAgentCode() + " 引用的工具不存在: " + toolClassName));
            snapshot.getTools().putIfAbsent(toolClassName,
                    WorkflowExecutionSnapshot.ImplementationSnapshot.from(tool));
        }
    }

    private void addModelSnapshot(WorkflowExecutionSnapshot snapshot, Long modelId) {
        if (snapshot.getModels().containsKey(modelId)) {
            return;
        }
        AiModelConfig model = requireModel(modelFactory.getModelConfig(modelId), "模型 " + modelId);
        snapshot.getModels().put(modelId, WorkflowExecutionSnapshot.ModelSnapshot.from(model));
    }

    private AiModelConfig requireModel(AiModelConfig model, String owner) {
        if (model == null || model.getId() == null) {
            throw new ServiceException(owner + " 没有可用的聊天模型配置");
        }
        if (model.getStatus() != null && !"1".equals(model.getStatus())) {
            throw new ServiceException(owner + " 引用的聊天模型已停用");
        }
        return model;
    }
}
