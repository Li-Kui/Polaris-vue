package com.polaris.ai.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.workflow.application.WorkflowApprovalApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowApprovalDecisionCommand;
import com.polaris.ai.workflow.application.WorkflowApprovalTaskView;
import com.polaris.ai.workflow.application.WorkflowTaskSignal;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.contract.WorkflowErrorCode;
import com.polaris.ai.workflow.domain.WorkflowApprovalTask;
import com.polaris.ai.workflow.domain.WorkflowExecution;
import com.polaris.ai.workflow.domain.WorkflowNodeRun;
import com.polaris.ai.workflow.mapper.WorkflowApprovalTaskMapper;
import com.polaris.ai.workflow.mapper.WorkflowExecutionMapper;
import com.polaris.ai.workflow.mapper.WorkflowNodeRunMapper;
import com.polaris.ai.workflow.runtime.WorkflowExecutionPersistence;
import com.polaris.ai.workflow.runtime.WorkflowQuotaService;
import com.polaris.common.exception.ServiceException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** 租户安全的审批箱、审批人校验和持久化审批决策服务。 */
@Service
public class WorkflowApprovalService implements WorkflowApprovalApplicationFacade {

    private static final Set<String> STATUSES = Set.of(
            "PENDING", "APPROVED", "REJECTED", "EXPIRED", "CANCELLED");

    private final WorkflowApprovalTaskMapper approvalTaskMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;
    private final WorkflowExecutionPersistence persistence;
    private final WorkflowQuotaService quotaService;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public WorkflowApprovalService(
            WorkflowApprovalTaskMapper approvalTaskMapper,
            WorkflowExecutionMapper executionMapper,
            WorkflowNodeRunMapper nodeRunMapper,
            WorkflowExecutionPersistence persistence,
            WorkflowQuotaService quotaService,
            WorkflowProperties properties,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        this.approvalTaskMapper = approvalTaskMapper;
        this.executionMapper = executionMapper;
        this.nodeRunMapper = nodeRunMapper;
        this.persistence = persistence;
        this.quotaService = quotaService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<WorkflowApprovalTaskView> list(String requestedStatus) {
        requireEnabled();
        String status = normalizeStatus(requestedStatus);
        LambdaQueryWrapper<WorkflowApprovalTask> query =
                new LambdaQueryWrapper<WorkflowApprovalTask>()
                        .orderByAsc(WorkflowApprovalTask::getDeadline)
                        .orderByDesc(WorkflowApprovalTask::getCreateTime)
                        .last("LIMIT 200");
        applyTenantScope(query, currentTenantId());
        if (status != null) {
            query.eq(WorkflowApprovalTask::getStatus, status);
        }
        CallerContext caller = CallerUtils.getContext();
        return approvalTaskMapper.selectList(query).stream()
                .filter(task -> caller.isSuperAdmin() || isAssigned(task, caller))
                .map(this::view)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowApprovalTaskView decide(
            String approvalTaskId, WorkflowApprovalDecisionCommand command) {
        requireEnabled();
        if (approvalTaskId == null || approvalTaskId.isBlank() || command == null) {
            throw new ServiceException("审批任务和审批决定不能为空");
        }
        String decision = command.decision() == null
                ? "" : command.decision().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVE", "REJECT").contains(decision)) {
            throw new ServiceException("审批决定只能是APPROVE或REJECT");
        }
        WorkflowApprovalTask preview = approvalTaskMapper.selectByTaskId(approvalTaskId);
        if (preview == null || !Objects.equals(preview.getTenantId(), currentTenantId())) {
            throw new ServiceException("审批任务不存在或无权访问");
        }
        WorkflowExecution execution = executionMapper.selectByExecutionIdForUpdate(
                preview.getExecutionId());
        WorkflowApprovalTask task = approvalTaskMapper.selectByTaskIdForUpdate(approvalTaskId);
        if (execution == null || task == null) {
            throw new ServiceException("审批任务不存在或无权访问");
        }
        if (!"PENDING".equals(task.getStatus())) {
            throw new ServiceException("审批任务已结束，不能重复处理");
        }
        if (command.expectedLockVersion() != null
                && !command.expectedLockVersion().equals(task.getLockVersion())) {
            throw new ServiceException("审批任务已被其他人更新，请刷新后重试");
        }
        if (task.getDeadline() != null && !task.getDeadline().after(new Date())) {
            expireLocked(task, execution);
            return view(task);
        }
        CallerContext caller = CallerUtils.getContext();
        if (!caller.isSuperAdmin() && !isAssigned(task, caller)) {
            throw new ServiceException("当前用户不是该任务的审批人");
        }
        String actorId = actorId(caller);
        if (!Boolean.TRUE.equals(task.getAllowSelfApproval())
                && actorId.equals(execution.getPrincipalId())) {
            throw new ServiceException("该审批任务禁止发起人自审");
        }
        ObjectNode summary = readSummary(task.getDecisionSummary());
        ArrayNode decisions = summary.withArray("decisions");
        for (JsonNode item : decisions) {
            if (actorId.equals(item.path("actorId").asText())) {
                throw new ServiceException("当前用户已经处理过该审批任务");
            }
        }
        enforceSequential(task, caller, decisions.size());
        ObjectNode item = decisions.addObject();
        item.put("actorId", actorId);
        item.put("actorName", safeActorName());
        item.put("decision", decision);
        item.put("decidedAt", System.currentTimeMillis());
        if (command.comment() != null && !command.comment().isBlank()) {
            item.put("comment", sanitizeComment(command.comment()));
        }
        boolean rejected = "REJECT".equals(decision);
        int approvedCount = countApproved(decisions);
        boolean approved = !rejected && approvedCount >= requiredApprovals(task);
        summary.put("approvedCount", approvedCount);
        summary.put("decisionCount", decisions.size());
        task.setDecisionSummary(writeJson(summary));
        task.setUpdateTime(new Date());
        persistence.appendEvent(execution.getExecutionId(), null, null,
                "APPROVAL_DECIDED", task.getNodeRunId(), null,
                Map.of("approvalTaskId", task.getApprovalTaskId(), "decision", decision));
        if (rejected || approved) {
            task.setStatus(rejected ? "REJECTED" : "APPROVED");
            task.setFinishTime(new Date());
        }
        if (approvalTaskMapper.updateById(task) != 1) {
            throw new ServiceException("审批任务已被其他人更新，请刷新后重试");
        }
        if (rejected) {
            rejectExecution(task, WorkflowErrorCode.APPROVAL_REJECTED, "工作流审批被拒绝");
        } else if (approved && executionMapper.requeueAfterApproval(task.getExecutionId()) != 1) {
            throw new ServiceException("审批完成，但工作流已不在等待状态");
        } else if (approved) {
            eventPublisher.publishEvent(WorkflowTaskSignal.APPROVAL_COMPLETED);
        }
        return view(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void expirePendingTasks() {
        if (!properties.isEnabled()) {
            return;
        }
        List<WorkflowApprovalTask> expired = approvalTaskMapper.selectList(
                new LambdaQueryWrapper<WorkflowApprovalTask>()
                        .eq(WorkflowApprovalTask::getStatus, "PENDING")
                        .le(WorkflowApprovalTask::getDeadline, new Date())
                        .orderByAsc(WorkflowApprovalTask::getDeadline)
                        .last("LIMIT 100"));
        for (WorkflowApprovalTask candidate : expired) {
            WorkflowExecution execution = executionMapper.selectByExecutionIdForUpdate(
                    candidate.getExecutionId());
            WorkflowApprovalTask task = approvalTaskMapper.selectByTaskIdForUpdate(
                    candidate.getApprovalTaskId());
            if (execution != null && task != null && "PENDING".equals(task.getStatus())
                    && task.getDeadline() != null && !task.getDeadline().after(new Date())) {
                expireLocked(task, execution);
            }
        }
    }

    private void expireLocked(WorkflowApprovalTask task, WorkflowExecution execution) {
        task.setStatus("EXPIRED");
        task.setFinishTime(new Date());
        task.setUpdateTime(new Date());
        if (approvalTaskMapper.updateById(task) != 1) {
            throw new ServiceException("审批任务过期状态更新冲突");
        }
        rejectExecution(task, WorkflowErrorCode.APPROVAL_EXPIRED, "工作流审批已超时");
    }

    private void rejectExecution(
            WorkflowApprovalTask task, WorkflowErrorCode errorCode, String message) {
        WorkflowNodeRun run = nodeRunMapper.selectOne(
                new LambdaQueryWrapper<WorkflowNodeRun>()
                        .eq(WorkflowNodeRun::getExecutionId, task.getExecutionId())
                        .eq(WorkflowNodeRun::getNodeRunId, task.getNodeRunId())
                        .eq(WorkflowNodeRun::getStatus, "WAITING")
                        .last("LIMIT 1"));
        if (run != null) {
            run.setStatus("FAILED");
            run.setErrorCode(errorCode.name());
            run.setErrorMessage(message);
            run.setFinishTime(new Date());
            nodeRunMapper.updateById(run);
        }
        if (executionMapper.rejectAfterApproval(
                task.getExecutionId(), errorCode.name(), message) != 1) {
            throw new ServiceException("审批任务对应执行已不在等待状态");
        }
        WorkflowExecution execution = executionMapper.selectByExecutionId(task.getExecutionId());
        quotaService.release(execution);
        persistence.appendEvent(task.getExecutionId(), null, null,
                "EXECUTION_REJECTED", task.getNodeRunId(), null,
                Map.of("approvalTaskId", task.getApprovalTaskId(), "errorCode", errorCode.name()));
    }

    private boolean isAssigned(WorkflowApprovalTask task, CallerContext caller) {
        JsonNode snapshot = readJson(task.getAssigneeSnapshot());
        String type = task.getAssigneeType();
        if ("USER".equals(type)) {
            return contains(snapshot.path("assigneeIds"), actorId(caller));
        }
        if ("DEPARTMENT".equals(type)) {
            return caller.getDeptId() != null
                    && contains(snapshot.path("assigneeIds"), String.valueOf(caller.getDeptId()));
        }
        if ("ROLE".equals(type)) {
            Set<String> authorities = currentAuthorities();
            for (JsonNode id : snapshot.path("assigneeIds")) {
                if (authorities.contains(id.asText()) || authorities.contains("ROLE_" + id.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private Long currentTenantId() {
        if (!CallerUtils.isPlatformMode()) {
            return null;
        }
        try {
            long tenantId = Long.parseLong(CallerUtils.getTenantId());
            if (tenantId <= 0) throw new NumberFormatException();
            return tenantId;
        } catch (Exception e) {
            throw new ServiceException("中台租户ID格式错误");
        }
    }

    private void applyTenantScope(
            LambdaQueryWrapper<WorkflowApprovalTask> query, Long tenantId) {
        if (tenantId == null) {
            query.isNull(WorkflowApprovalTask::getTenantId);
        } else {
            query.eq(WorkflowApprovalTask::getTenantId, tenantId);
        }
    }

    private void enforceSequential(
            WorkflowApprovalTask task, CallerContext caller, int decisionCount) {
        if (!"SEQUENTIAL".equals(task.getApprovalMode())) {
            return;
        }
        JsonNode ids = readJson(task.getAssigneeSnapshot()).path("assigneeIds");
        if (!ids.isArray() || decisionCount >= ids.size()
                || !actorId(caller).equals(ids.get(decisionCount).asText())) {
            throw new ServiceException("顺序审批尚未轮到当前用户");
        }
    }

    private int requiredApprovals(WorkflowApprovalTask task) {
        JsonNode ids = readJson(task.getAssigneeSnapshot()).path("assigneeIds");
        int assigneeCount = ids.isArray() ? ids.size() : 0;
        return switch (task.getApprovalMode()) {
            case "ALL", "SEQUENTIAL" -> Math.max(1, assigneeCount);
            case "N_OF_M" -> Math.max(1, Math.min(task.getRequiredApprovals(), assigneeCount));
            default -> 1;
        };
    }

    private int countApproved(ArrayNode decisions) {
        int result = 0;
        for (JsonNode decision : decisions) {
            if ("APPROVE".equals(decision.path("decision").asText())) result++;
        }
        return result;
    }

    private String actorId(CallerContext caller) {
        String username = caller.getUsername();
        if (username != null && username.startsWith("apikey:")) {
            throw new ServiceException("API Key 不能执行人工审批");
        }
        if (caller.getUserId() == null) {
            throw new ServiceException("当前审批用户身份不完整");
        }
        return String.valueOf(caller.getUserId());
    }

    private String safeActorName() {
        String value = CallerUtils.getUsername();
        if (value == null || value.isBlank()) return "unknown";
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private String sanitizeComment(String comment) {
        String value = comment.replaceAll("[\\r\\n\\t]+", " ").trim();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private boolean contains(JsonNode values, String expected) {
        if (!values.isArray()) return false;
        for (JsonNode value : values) {
            if (expected.equals(value.asText())) return true;
        }
        return false;
    }

    private Set<String> currentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) return Set.of();
        Set<String> result = new HashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            result.add(authority.getAuthority());
        }
        return result;
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(result)) throw new ServiceException("审批状态无效");
        return result;
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new ServiceException("工作流 尚未启用");
    }

    private ObjectNode readSummary(String value) {
        JsonNode node = readJson(value);
        return node.isObject() ? (ObjectNode) node : objectMapper.createObjectNode();
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank()
                    ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (Exception e) {
            throw new ServiceException("审批任务快照格式无效");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ServiceException("审批决定序列化失败");
        }
    }

    private WorkflowApprovalTaskView view(WorkflowApprovalTask task) {
        return new WorkflowApprovalTaskView(
                task.getApprovalTaskId(), task.getExecutionId(), task.getNodeRunId(),
                task.getAssigneeType(), task.getApprovalMode(), task.getRequiredApprovals(),
                task.getAllowSelfApproval(), task.getStatus(), task.getDecisionSummary(),
                task.getDeadline(), task.getLockVersion(), task.getCreateTime(), task.getFinishTime());
    }
}
