package com.polaris.ai.workflow.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.workflow.application.WorkflowTaskSignal;
import com.polaris.ai.workflow.domain.*;
import com.polaris.ai.workflow.mapper.*;
import com.polaris.ai.workflow.security.WorkflowDataRedactor;
import com.polaris.common.exception.ServiceException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

/** 状态、事件和检查点变更的事务边界。 */
@Component
public class WorkflowExecutionPersistence {

    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;
    private final WorkflowEventMapper eventMapper;
    private final WorkflowCheckpointMapper checkpointMapper;
    private final WorkflowApprovalInstanceMapper approvalInstanceMapper;
    private final WorkflowApprovalStageMapper approvalStageMapper;
    private final WorkflowApprovalAssignmentMapper approvalAssignmentMapper;
    private final WorkflowQuotaService quotaService;
    private final WorkflowOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final WorkflowDataRedactor dataRedactor;
    private final ApplicationEventPublisher eventPublisher;

    public WorkflowExecutionPersistence(
            WorkflowExecutionMapper executionMapper,
            WorkflowNodeRunMapper nodeRunMapper,
            WorkflowEventMapper eventMapper,
            WorkflowCheckpointMapper checkpointMapper,
            WorkflowApprovalInstanceMapper approvalInstanceMapper,
            WorkflowApprovalStageMapper approvalStageMapper,
            WorkflowApprovalAssignmentMapper approvalAssignmentMapper,
            WorkflowQuotaService quotaService,
            WorkflowOutboxMapper outboxMapper,
            ObjectMapper objectMapper,
            WorkflowDataRedactor dataRedactor,
            ApplicationEventPublisher eventPublisher) {
        this.executionMapper = executionMapper;
        this.nodeRunMapper = nodeRunMapper;
        this.eventMapper = eventMapper;
        this.checkpointMapper = checkpointMapper;
        this.approvalInstanceMapper = approvalInstanceMapper;
        this.approvalStageMapper = approvalStageMapper;
        this.approvalAssignmentMapper = approvalAssignmentMapper;
        this.quotaService = quotaService;
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
        this.dataRedactor = dataRedactor;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public void createQueued(WorkflowExecution execution) {
        execution.setEventSequence(0L);
        execution.setCheckpointSequence(0L);
        if (executionMapper.insert(execution) != 1) {
            throw new ServiceException("创建工作流执行失败");
        }
        appendEventLocked(execution, "EXECUTION_QUEUED", null, null, Map.of());
        WorkflowOutbox outbox = new WorkflowOutbox();
        outbox.setTenantId(execution.getTenantId());
        outbox.setEventId(UUID.randomUUID().toString());
        outbox.setAggregateType("WORKFLOW_EXECUTION");
        outbox.setAggregateId(execution.getExecutionId());
        outbox.setEventType("WORKFLOW_EXECUTION_QUEUED");
        outbox.setPayloadJson(writeJson(Map.of(
                "executionId", execution.getExecutionId(),
                "workflowCode", execution.getWorkflowCode(),
                "workflowVersionId", execution.getWorkflowVersionId())));
        outbox.setPublishStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setCreateTime(new Date());
        outbox.setUpdateTime(new Date());
        if (outboxMapper.insert(outbox) != 1) {
            throw new ServiceException("创建工作流事务事件失败");
        }
        eventPublisher.publishEvent(WorkflowTaskSignal.EXECUTION_QUEUED);
    }

    @Transactional(rollbackFor = Exception.class)
    public long appendEvent(
            String executionId,
            String runnerId,
            Long fencingToken,
            String eventType,
            String nodeRunId,
            String nodeId,
            Object payload) {
        WorkflowExecution execution = lockAndCheckFence(executionId, runnerId, fencingToken);
        return appendEventLocked(execution, eventType, nodeRunId, nodeId, payload);
    }

    /** 在当前事务中登记可由站内信、邮件或IM消费者处理的可靠领域事件。 */
    public void appendOutboxEvent(
            Long tenantId,
            String aggregateType,
            String aggregateId,
            String eventType,
            Object payload) {
        WorkflowOutbox outbox = new WorkflowOutbox();
        outbox.setTenantId(tenantId);
        outbox.setEventId(UUID.randomUUID().toString());
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayloadJson(writeJson(payload));
        outbox.setPublishStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setCreateTime(new Date());
        outbox.setUpdateTime(new Date());
        if (outboxMapper.insert(outbox) != 1) {
            throw new ServiceException("创建工作流通知事件失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void startNode(
            String executionId,
            String runnerId,
            long fencingToken,
            WorkflowNodeRun nodeRun) {
        WorkflowExecution execution = lockAndCheckFence(executionId, runnerId, fencingToken);
        if (nodeRunMapper.insert(nodeRun) != 1) {
            throw new ServiceException("创建节点运行记录失败");
        }
        appendEventLocked(execution, "NODE_STARTED", nodeRun.getNodeRunId(),
                nodeRun.getNodeId(), Map.of("attemptNo", nodeRun.getAttemptNo()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeNodeAndCheckpoint(
            String executionId,
            String runnerId,
            long fencingToken,
            WorkflowNodeRun nodeRun,
            String stateJson,
            String usageJson,
            String planHash) {
        WorkflowExecution execution = lockAndCheckFence(executionId, runnerId, fencingToken);
        if (nodeRunMapper.updateById(nodeRun) != 1) {
            throw new ServiceException("更新节点运行记录失败");
        }
        long checkpointSequence = execution.getCheckpointSequence() + 1;
        execution.setCheckpointSequence(checkpointSequence);
        execution.setUsageJson(usageJson);
        if (executionMapper.updateById(execution) != 1) {
            throw new ServiceException("推进工作流检查点序列失败");
        }
        WorkflowCheckpoint checkpoint = new WorkflowCheckpoint();
        checkpoint.setTenantId(execution.getTenantId());
        checkpoint.setExecutionId(executionId);
        checkpoint.setSequenceNo(checkpointSequence);
        checkpoint.setPlanHash(planHash);
        checkpoint.setNodeRunId(nodeRun.getNodeRunId());
        checkpoint.setStateJson(stateJson);
        checkpoint.setStatus("SAFE");
        checkpoint.setCreateTime(new Date());
        if (checkpointMapper.insert(checkpoint) != 1) {
            throw new ServiceException("保存工作流检查点失败");
        }
        appendEventLocked(execution, "NODE_SUCCEEDED", nodeRun.getNodeRunId(),
                nodeRun.getNodeId(), Map.of("attemptNo", nodeRun.getAttemptNo()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void skipNodeAndCheckpoint(
            String executionId,
            String runnerId,
            long fencingToken,
            WorkflowNodeRun skippedRun,
            String stateJson,
            String planHash,
            String errorCode) {
        WorkflowExecution execution = lockAndCheckFence(executionId, runnerId, fencingToken);
        if (nodeRunMapper.insert(skippedRun) != 1) {
            throw new ServiceException("保存跳过节点记录失败");
        }
        long checkpointSequence = execution.getCheckpointSequence() + 1;
        execution.setCheckpointSequence(checkpointSequence);
        if (executionMapper.updateById(execution) != 1) {
            throw new ServiceException("推进跳过节点检查点失败");
        }
        WorkflowCheckpoint checkpoint = new WorkflowCheckpoint();
        checkpoint.setTenantId(execution.getTenantId());
        checkpoint.setExecutionId(executionId);
        checkpoint.setSequenceNo(checkpointSequence);
        checkpoint.setPlanHash(planHash);
        checkpoint.setNodeRunId(skippedRun.getNodeRunId());
        checkpoint.setStateJson(stateJson);
        checkpoint.setStatus("SAFE");
        checkpoint.setCreateTime(new Date());
        if (checkpointMapper.insert(checkpoint) != 1) {
            throw new ServiceException("保存跳过节点检查点失败");
        }
        appendEventLocked(execution, "NODE_SKIPPED", skippedRun.getNodeRunId(),
                skippedRun.getNodeId(), Map.of(
                        "attemptNo", skippedRun.getAttemptNo(),
                        "errorCode", errorCode));
    }

    @Transactional(rollbackFor = Exception.class)
    public void failNode(
            String executionId,
            String runnerId,
            long fencingToken,
            WorkflowNodeRun nodeRun) {
        WorkflowExecution execution = lockAndCheckFence(executionId, runnerId, fencingToken);
        if (nodeRunMapper.updateById(nodeRun) != 1) {
            throw new ServiceException("更新失败节点记录失败");
        }
        appendEventLocked(execution, "NODE_FAILED", nodeRun.getNodeRunId(),
                nodeRun.getNodeId(), Map.of(
                        "attemptNo", nodeRun.getAttemptNo(),
                "errorCode", String.valueOf(nodeRun.getErrorCode())));
    }

    @Transactional(rollbackFor = Exception.class)
    public void suspendForApproval(
            String executionId,
            String runnerId,
            long fencingToken,
            WorkflowNodeRun nodeRun,
            WorkflowApprovalInstance instance,
            WorkflowApprovalStage stage,
            java.util.List<WorkflowApprovalAssignment> assignments,
            String stateJson,
            String planHash) {
        WorkflowExecution execution = lockAndCheckFence(executionId, runnerId, fencingToken);
        if (nodeRunMapper.insert(nodeRun) != 1
                || approvalInstanceMapper.insert(instance) != 1
                || approvalStageMapper.insert(stage) != 1) {
            throw new ServiceException("创建工作流审批实例失败");
        }
        for (WorkflowApprovalAssignment assignment : assignments) {
            if (approvalAssignmentMapper.insert(assignment) != 1) {
                throw new ServiceException("固化工作流审批人员失败");
            }
        }
        saveCheckpointLocked(execution, nodeRun.getNodeRunId(), stateJson, planHash);
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("approvalInstanceId", instance.getApprovalInstanceId());
        payload.put("stageInstanceId", stage.getStageInstanceId());
        payload.put("assigneeCount", assignments.size());
        if (stage.getDeadline() != null) payload.put("deadline", stage.getDeadline().getTime());
        appendEventLocked(execution,
                "CONFIG_ERROR".equals(instance.getStatus())
                        ? "APPROVAL_CONFIG_ERROR" : "APPROVAL_CREATED",
                nodeRun.getNodeRunId(), nodeRun.getNodeId(), payload);
        if ("CONFIG_ERROR".equals(instance.getStatus())) {
            nodeRun.setStatus("NEEDS_ATTENTION");
            nodeRun.setErrorCode("APPROVAL_CONFIG_ERROR");
            nodeRun.setErrorMessage("审批级别没有有效审批人，或通过人数配置无法满足");
            nodeRunMapper.updateById(nodeRun);
            appendEventLocked(execution, "EXECUTION_NEEDS_ATTENTION",
                    nodeRun.getNodeRunId(), nodeRun.getNodeId(),
                    Map.of("reason", "APPROVAL_CONFIG_ERROR"));
            execution.setStatus("NEEDS_ATTENTION");
        } else {
            appendEventLocked(execution, "EXECUTION_WAITING", nodeRun.getNodeRunId(),
                    nodeRun.getNodeId(), Map.of("reason", "APPROVAL"));
            execution.setStatus("WAITING_APPROVAL");
        }
        execution.setRunnerId(null);
        execution.setLeaseUntil(null);
        execution.setHeartbeatTime(new Date());
        if (executionMapper.updateById(execution) != 1) {
            throw new ServiceException("挂起工作流审批失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void suspendForWait(
            String executionId,
            String runnerId,
            long fencingToken,
            WorkflowNodeRun nodeRun,
            Date resumeTime,
            String stateJson,
            String planHash) {
        WorkflowExecution execution = lockAndCheckFence(executionId, runnerId, fencingToken);
        if (nodeRunMapper.insert(nodeRun) != 1) {
            throw new ServiceException("创建等待节点运行记录失败");
        }
        saveCheckpointLocked(execution, nodeRun.getNodeRunId(), stateJson, planHash);
        appendEventLocked(execution, "EXECUTION_WAITING", nodeRun.getNodeRunId(),
                nodeRun.getNodeId(), Map.of(
                        "reason", "TIMER",
                        "resumeTime", resumeTime.getTime()));
        execution.setStatus("WAITING_EVENT");
        execution.setResumeTime(resumeTime);
        execution.setRunnerId(null);
        execution.setLeaseUntil(null);
        execution.setHeartbeatTime(new Date());
        if (executionMapper.updateById(execution) != 1) {
            throw new ServiceException("挂起工作流等待节点失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void suspendForRetry(
            String executionId,
            String runnerId,
            long fencingToken,
            String nodeRunId,
            String nodeId,
            int nextAttemptNo,
            long delayMs,
            Date resumeTime,
            String stateJson,
            String planHash) {
        WorkflowExecution execution = lockAndCheckFence(executionId, runnerId, fencingToken);
        saveCheckpointLocked(execution, nodeRunId, stateJson, planHash);
        appendEventLocked(execution, "NODE_RETRY_SCHEDULED", nodeRunId, nodeId,
                Map.of("attemptNo", nextAttemptNo, "delayMs", delayMs,
                        "resumeTime", resumeTime.getTime()));
        appendEventLocked(execution, "EXECUTION_WAITING", nodeRunId, nodeId,
                Map.of("reason", "RETRY", "resumeTime", resumeTime.getTime()));
        execution.setStatus("WAITING_EVENT");
        execution.setResumeTime(resumeTime);
        execution.setRunnerId(null);
        execution.setLeaseUntil(null);
        execution.setHeartbeatTime(new Date());
        if (executionMapper.updateById(execution) != 1) {
            throw new ServiceException("挂起工作流重试失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void suspendForChild(
            String executionId,
            String runnerId,
            long fencingToken,
            WorkflowNodeRun nodeRun,
            boolean createNodeRun,
            String childExecutionId,
            Date resumeTime,
            String stateJson,
            String planHash) {
        WorkflowExecution execution = lockAndCheckFence(executionId, runnerId, fencingToken);
        if (createNodeRun && nodeRunMapper.insert(nodeRun) != 1) {
            throw new ServiceException("创建子工作流节点运行记录失败");
        }
        saveCheckpointLocked(execution, nodeRun.getNodeRunId(), stateJson, planHash);
        appendEventLocked(execution, "EXECUTION_WAITING",
                nodeRun.getNodeRunId(), nodeRun.getNodeId(), Map.of(
                        "reason", "SUB_WORKFLOW",
                        "childExecutionId", childExecutionId,
                        "resumeTime", resumeTime.getTime()));
        execution.setStatus("WAITING_EVENT");
        execution.setResumeTime(resumeTime);
        execution.setRunnerId(null);
        execution.setLeaseUntil(null);
        execution.setHeartbeatTime(new Date());
        if (executionMapper.updateById(execution) != 1) {
            throw new ServiceException("挂起工作流等待子工作流失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void finishExecution(
            String executionId,
            String runnerId,
            long fencingToken,
            String status,
            String outputJson,
            String errorCode,
            String errorMessage,
            String eventType) {
        WorkflowExecution execution = lockAndCheckFence(executionId, runnerId, fencingToken);
        appendEventLocked(execution, eventType, null, null,
                errorCode == null ? Map.of() : Map.of("errorCode", errorCode));
        execution.setStatus(status);
        execution.setOutputJson(outputJson);
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorMessage);
        execution.setFinishTime(new Date());
        execution.setRunnerId(null);
        execution.setLeaseUntil(null);
        execution.setHeartbeatTime(new Date());
        quotaService.release(execution);
        if (executionMapper.updateById(execution) != 1) {
            throw new ServiceException("结束工作流执行失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void needsAttention(
            String executionId,
            String runnerId,
            long fencingToken,
            String errorCode,
            String errorMessage) {
        WorkflowExecution execution = lockAndCheckFence(executionId, runnerId, fencingToken);
        appendEventLocked(execution, "EXECUTION_NEEDS_ATTENTION", null, null,
                Map.of("errorCode", errorCode));
        execution.setStatus("NEEDS_ATTENTION");
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorMessage);
        execution.setRunnerId(null);
        execution.setLeaseUntil(null);
        execution.setHeartbeatTime(new Date());
        if (executionMapper.updateById(execution) != 1) {
            throw new ServiceException("挂起工作流执行失败");
        }
    }

    private WorkflowExecution lockAndCheckFence(
            String executionId, String runnerId, Long fencingToken) {
        WorkflowExecution execution = executionMapper.selectByExecutionIdForUpdate(executionId);
        if (execution == null) {
            throw new ServiceException("工作流执行不存在");
        }
        if (runnerId != null && (!runnerId.equals(execution.getRunnerId())
                || fencingToken == null || !fencingToken.equals(execution.getFencingToken()))) {
            throw new ServiceException("工作流执行租约已失效");
        }
        return execution;
    }

    private void saveCheckpointLocked(
            WorkflowExecution execution,
            String nodeRunId,
            String stateJson,
            String planHash) {
        long checkpointSequence = execution.getCheckpointSequence() + 1;
        execution.setCheckpointSequence(checkpointSequence);
        if (executionMapper.updateById(execution) != 1) {
            throw new ServiceException("推进等待节点检查点失败");
        }
        WorkflowCheckpoint checkpoint = new WorkflowCheckpoint();
        checkpoint.setTenantId(execution.getTenantId());
        checkpoint.setExecutionId(execution.getExecutionId());
        checkpoint.setSequenceNo(checkpointSequence);
        checkpoint.setPlanHash(planHash);
        checkpoint.setNodeRunId(nodeRunId);
        checkpoint.setStateJson(stateJson);
        checkpoint.setStatus("SAFE");
        checkpoint.setCreateTime(new Date());
        if (checkpointMapper.insert(checkpoint) != 1) {
            throw new ServiceException("保存等待节点检查点失败");
        }
    }

    private long appendEventLocked(
            WorkflowExecution execution,
            String eventType,
            String nodeRunId,
            String nodeId,
            Object payload) {
        long sequence = execution.getEventSequence() + 1;
        execution.setEventSequence(sequence);
        if (executionMapper.updateById(execution) != 1) {
            throw new ServiceException("推进工作流事件序列失败");
        }
        WorkflowEvent event = new WorkflowEvent();
        event.setTenantId(execution.getTenantId());
        event.setExecutionId(execution.getExecutionId());
        event.setSequenceNo(sequence);
        event.setEventType(eventType);
        event.setNodeRunId(nodeRunId);
        event.setNodeId(nodeId);
        event.setPayloadJson(writeJson(dataRedactor.redact(objectMapper.valueToTree(payload))));
        event.setCreateTime(new Date());
        if (eventMapper.insert(event) != 1) {
            throw new ServiceException("保存工作流事件失败");
        }
        return sequence;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new ServiceException("工作流事件序列化失败");
        }
    }
}
