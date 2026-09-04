package com.polaris.ai.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.workflow.application.*;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.contract.WorkflowErrorCode;
import com.polaris.ai.workflow.contract.WorkflowPermission;
import com.polaris.ai.workflow.domain.*;
import com.polaris.ai.workflow.mapper.*;
import com.polaris.ai.workflow.runtime.*;
import com.polaris.ai.workflow.spi.WorkflowApprovalDirectoryEntry;
import com.polaris.ai.workflow.spi.WorkflowApprovalPrincipal;
import com.polaris.ai.workflow.spi.WorkflowApprovalTarget;
import com.polaris.common.exception.ServiceException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 租户安全的审批箱、审批人校验和持久化审批决策服务。 */
@Service
public class WorkflowApprovalService implements WorkflowApprovalApplicationFacade {

    private static final Set<String> STATUSES = Set.of(
            "PENDING", "APPROVED", "REJECTED", "EXPIRED", "CANCELLED", "CONFIG_ERROR");

    private final WorkflowApprovalInstanceMapper approvalInstanceMapper;
    private final WorkflowApprovalStageMapper approvalStageMapper;
    private final WorkflowApprovalAssignmentMapper approvalAssignmentMapper;
    private final WorkflowApprovalDecisionMapper approvalDecisionMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;
    private final WorkflowExecutionPersistence persistence;
    private final WorkflowQuotaService quotaService;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkflowApprovalDecisionEngine decisionEngine;
    private final WorkflowApprovalV2Factory approvalV2Factory;
    private final WorkflowApprovalDirectoryResolver directoryResolver;

    public WorkflowApprovalService(
            WorkflowApprovalInstanceMapper approvalInstanceMapper,
            WorkflowApprovalStageMapper approvalStageMapper,
            WorkflowApprovalAssignmentMapper approvalAssignmentMapper,
            WorkflowApprovalDecisionMapper approvalDecisionMapper,
            WorkflowExecutionMapper executionMapper,
            WorkflowNodeRunMapper nodeRunMapper,
            WorkflowExecutionPersistence persistence,
            WorkflowQuotaService quotaService,
            WorkflowProperties properties,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            WorkflowApprovalDecisionEngine decisionEngine,
            WorkflowApprovalV2Factory approvalV2Factory,
            WorkflowApprovalDirectoryResolver directoryResolver) {
        this.approvalInstanceMapper = approvalInstanceMapper;
        this.approvalStageMapper = approvalStageMapper;
        this.approvalAssignmentMapper = approvalAssignmentMapper;
        this.approvalDecisionMapper = approvalDecisionMapper;
        this.executionMapper = executionMapper;
        this.nodeRunMapper = nodeRunMapper;
        this.persistence = persistence;
        this.quotaService = quotaService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.decisionEngine = decisionEngine;
        this.approvalV2Factory = approvalV2Factory;
        this.directoryResolver = directoryResolver;
    }

    @Override
    public List<WorkflowApprovalTaskView> list(String requestedStatus) {
        requireEnabled();
        String status = normalizeStatus(requestedStatus);
        CallerContext caller = CallerUtils.getContext();
        return listInstances(status, caller);
    }

    @Override
    public WorkflowApprovalTaskView get(String approvalInstanceId) {
        requireEnabled();
        if (approvalInstanceId == null || approvalInstanceId.isBlank()) {
            throw new ServiceException("审批任务不能为空");
        }
        CallerContext caller = CallerUtils.getContext();
        WorkflowApprovalInstance instance = approvalInstanceMapper.selectByInstanceId(
                approvalInstanceId);
        if (instance == null || !Objects.equals(instance.getTenantId(), currentTenantId())
                || !caller.isSuperAdmin() && !caller.hasPermission(WorkflowPermission.ADMIN)
                && !assignedToInstance(instance, actorId(caller))) {
            throw new ServiceException("审批任务不存在或无权访问");
        }
        return view(instance, currentStage(instance), caller);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowApprovalTaskView decide(
            String approvalInstanceId, WorkflowApprovalDecisionCommand command) {
        requireEnabled();
        if (approvalInstanceId == null || approvalInstanceId.isBlank() || command == null) {
            throw new ServiceException("审批任务和审批决定不能为空");
        }
        String decision = command.decision() == null
                ? "" : command.decision().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVE", "REJECT").contains(decision)) {
            throw new ServiceException("审批决定只能是APPROVE或REJECT");
        }
        WorkflowApprovalInstance preview = approvalInstanceMapper.selectByInstanceId(
                approvalInstanceId);
        if (preview == null || !Objects.equals(preview.getTenantId(), currentTenantId())) {
            throw new ServiceException("审批任务不存在或无权访问");
        }
        return decideInstance(preview, command, decision);
    }

    @Override
    public List<WorkflowApprovalDirectoryEntry> listDirectory(String keyword) {
        requireEnabled();
        return directoryResolver.list(currentTenantId(), keyword);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowApprovalTaskView repair(
            String approvalInstanceId, WorkflowApprovalRepairCommand command) {
        requireEnabled();
        if (approvalInstanceId == null || approvalInstanceId.isBlank() || command == null
                || command.targets() == null || command.targets().isEmpty()) {
            throw new ServiceException("请选择用于恢复审批的成员、角色或部门");
        }
        CallerContext operator = CallerUtils.getContext();
        if (!operator.isSuperAdmin() && !operator.hasPermission(WorkflowPermission.ADMIN)) {
            throw new ServiceException("当前用户没有审批异常处置权限");
        }
        if (command.targets().size() > 20) {
            throw new ServiceException("一次最多选择20组审批对象");
        }
        String reason = command.reason() == null ? "" : sanitizeComment(command.reason());
        if (reason.isBlank()) throw new ServiceException("请填写修复原因");
        WorkflowApprovalInstance preview = approvalInstanceMapper.selectByInstanceId(
                approvalInstanceId);
        if (preview == null || !Objects.equals(preview.getTenantId(), currentTenantId())) {
            throw new ServiceException("审批任务不存在或无权访问");
        }
        WorkflowExecution execution = executionMapper.selectByExecutionIdForUpdate(
                preview.getExecutionId());
        WorkflowApprovalInstance instance = approvalInstanceMapper.selectByInstanceIdForUpdate(
                preview.getApprovalInstanceId());
        if (execution == null || instance == null
                || !"CONFIG_ERROR".equals(instance.getStatus())) {
            throw new ServiceException("只有配置异常的审批任务可以修复");
        }
        if (command.expectedLockVersion() != null
                && !command.expectedLockVersion().equals(instance.getLockVersion())) {
            throw new ServiceException("审批任务已被其他人更新，请刷新后重试");
        }
        if (!"NEEDS_ATTENTION".equals(execution.getStatus())
                || Boolean.TRUE.equals(execution.getCancelRequested())) {
            throw new ServiceException("工作流已不在可恢复状态");
        }
        WorkflowApprovalStage stage = approvalStageMapper.selectByStageIdForUpdate(
                instance.getCurrentStageId());
        if (stage == null || !"CONFIG_ERROR".equals(stage.getStatus())) {
            throw new ServiceException("当前审批级别已不需要修复");
        }
        JsonNode config = readJson(instance.getConfigSnapshot());
        List<WorkflowApprovalPrincipal> principals;
        try {
            principals = new ArrayList<>(directoryResolver.resolve(
                    instance.getTenantId(), command.targets()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ServiceException("选择的审批对象当前无法解析，请重新选择");
        }
        if (!config.path("options").path("allowSelfApproval").asBoolean(false)
                && execution.getPrincipalId() != null) {
            principals.removeIf(item -> execution.getPrincipalId().equals(item.userId()));
        }
        if (principals.isEmpty()) {
            throw new ServiceException("所选对象没有可用审批人，或只包含禁止自审的发起人");
        }
        List<WorkflowApprovalAssignment> existing = approvalAssignmentMapper.selectByStageId(
                stage.getStageInstanceId());
        Map<String, WorkflowApprovalAssignment> active = new LinkedHashMap<>();
        for (WorkflowApprovalAssignment item : existing) {
            if ("PENDING".equals(item.getStatus())) active.put(item.getUserId(), item);
        }
        Date now = new Date();
        Set<String> repairedUsers = new LinkedHashSet<>(active.keySet());
        principals.forEach(principal -> repairedUsers.add(principal.userId()));
        if (repairedUsers.size() > 500) {
            throw new ServiceException("单级审批人数不能超过500人");
        }
        for (WorkflowApprovalPrincipal principal : principals) {
            if (active.containsKey(principal.userId())) continue;
            WorkflowApprovalAssignment assignment = repairAssignment(
                    instance, stage, principal, now);
            if (approvalAssignmentMapper.insert(assignment) != 1) {
                throw new ServiceException("补充审批人失败，请刷新后重试");
            }
            active.put(principal.userId(), assignment);
        }
        JsonNode policy = readJson(stage.getPolicySnapshot());
        String mode = policy.path("mode").asText("ANY");
        int required = "ALL".equals(mode) ? active.size()
                : "N_OF_M".equals(mode) ? policy.path("requiredApprovals").asInt(0) : 1;
        if (required < 1 || required > active.size()) {
            throw new ServiceException("补充后审批人数仍不足以满足通过规则");
        }
        Date instanceDeadline = approvalDeadline(config.path("deadline"), now);
        JsonNode stageConfig = config.path("stages").path(stage.getSequenceNo() - 1);
        Date stageDeadline = approvalDeadline(stageConfig.path("deadline"), now);
        stage.setRequiredApprovals(required);
        stage.setApprovedCount(0);
        stage.setRejectedCount(0);
        stage.setPendingCount(active.size());
        stage.setDeadline(earliest(stageDeadline, instanceDeadline));
        stage.setStatus("ACTIVE");
        stage.setFinishTime(null);
        stage.setUpdateTime(now);
        if (approvalStageMapper.updateById(stage) != 1) {
            throw new ServiceException("恢复审批级别失败，请刷新后重试");
        }
        instance.setStatus("PENDING");
        instance.setDeadline(instanceDeadline);
        instance.setReminderSentTime(null);
        instance.setReminderTime(reminderAt(
                config.path("reminder"), earliest(stage.getDeadline(), instanceDeadline), now));
        instance.setFinishTime(null);
        instance.setUpdateTime(now);
        if (approvalInstanceMapper.updateById(instance) != 1) {
            throw new ServiceException("恢复审批任务失败，请刷新后重试");
        }
        WorkflowNodeRun run = nodeRunMapper.selectOne(
                new LambdaQueryWrapper<WorkflowNodeRun>()
                        .eq(WorkflowNodeRun::getExecutionId, instance.getExecutionId())
                        .eq(WorkflowNodeRun::getNodeRunId, instance.getNodeRunId())
                        .eq(WorkflowNodeRun::getStatus, "NEEDS_ATTENTION")
                        .last("LIMIT 1"));
        if (run == null) throw new ServiceException("审批节点已不在可恢复状态");
        run.setStatus("WAITING");
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.setFinishTime(null);
        run.setUpdateTime(now);
        if (nodeRunMapper.updateById(run) != 1) {
            throw new ServiceException("恢复审批节点失败，请刷新后重试");
        }
        execution.setStatus("WAITING_APPROVAL");
        execution.setErrorCode(null);
        execution.setErrorMessage(null);
        execution.setFinishTime(null);
        execution.setHeartbeatTime(now);
        execution.setUpdateTime(now);
        if (executionMapper.updateById(execution) != 1) {
            throw new ServiceException("恢复工作流失败，请刷新后重试");
        }
        persistence.appendEvent(execution.getExecutionId(), null, null,
                "APPROVAL_REPAIRED", instance.getNodeRunId(), null,
                Map.of("approvalInstanceId", instance.getApprovalInstanceId(),
                        "stageInstanceId", stage.getStageInstanceId(),
                        "operator", safeActorName(), "reason", reason));
        return view(instance, stage, operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public void expirePendingTasks() {
        if (!properties.isEnabled()) {
            return;
        }
        sendDueReminders();
        expireInstances();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowApprovalTaskView reassign(
            String approvalInstanceId, WorkflowApprovalReassignCommand command) {
        requireAdminCommand(approvalInstanceId, command == null ? null : command.targets(),
                command == null ? null : command.reason());
        LockedApproval locked = lockPendingApproval(approvalInstanceId,
                command.expectedLockVersion());
        List<WorkflowApprovalPrincipal> principals = resolvePrincipals(
                locked.instance(), locked.execution(), command.targets());
        Date now = new Date();
        List<WorkflowApprovalAssignment> assignments = approvalAssignmentMapper
                .selectByStageId(locked.stage().getStageInstanceId());
        for (WorkflowApprovalAssignment assignment : assignments) {
            if ("PENDING".equals(assignment.getStatus())) {
                assignment.setStatus("REASSIGNED");
                assignment.setUpdateTime(now);
                approvalAssignmentMapper.updateById(assignment);
            }
        }
        Map<String, WorkflowApprovalAssignment> existing = assignments.stream()
                .collect(java.util.stream.Collectors.toMap(
                        WorkflowApprovalAssignment::getUserId, item -> item, (a, b) -> a));
        for (WorkflowApprovalPrincipal principal : principals) {
            WorkflowApprovalAssignment assignment = existing.get(principal.userId());
            if (assignment != null && Set.of("APPROVED", "REJECTED")
                    .contains(assignment.getStatus())) continue;
            if (assignment == null) {
                assignment = repairAssignment(locked.instance(), locked.stage(), principal, now);
                if (approvalAssignmentMapper.insert(assignment) != 1) {
                    throw new ServiceException("重新指派审批人失败");
                }
            } else {
                assignment.setUsername(principal.username());
                assignment.setDisplayName(principal.displayName());
                assignment.setDepartmentId(principal.departmentId());
                assignment.setDepartmentName(principal.departmentName());
                assignment.setSourceSnapshot(writeJson(principal.sources()));
                assignment.setStatus("PENDING");
                assignment.setUpdateTime(now);
                if (approvalAssignmentMapper.updateById(assignment) != 1) {
                    throw new ServiceException("重新指派审批人失败");
                }
            }
        }
        refreshStageCounts(locked.stage());
        ensureReachable(locked.stage());
        locked.instance().setUpdateTime(now);
        if (approvalInstanceMapper.updateById(locked.instance()) != 1) {
            throw new ServiceException("审批任务已被其他人更新，请刷新后重试");
        }
        auditManagementAction(locked, "APPROVAL_REASSIGNED", command.reason(), principals);
        return view(locked.instance(), locked.stage(), CallerUtils.getContext());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowApprovalTaskView restartStage(
            String approvalInstanceId, WorkflowApprovalRestartStageCommand command) {
        requireAdminCommand(approvalInstanceId, command == null ? null : command.targets(),
                command == null ? null : command.reason());
        LockedApproval locked = lockPendingApproval(approvalInstanceId,
                command.expectedLockVersion());
        List<WorkflowApprovalPrincipal> principals = resolvePrincipals(
                locked.instance(), locked.execution(), command.targets());
        Date now = new Date();
        JsonNode config = readJson(locked.instance().getConfigSnapshot());
        locked.instance().setDeadline(approvalDeadline(config.path("deadline"), now));
        locked.stage().setStatus("SUPERSEDED");
        locked.stage().setFinishTime(now);
        locked.stage().setUpdateTime(now);
        if (approvalStageMapper.updateById(locked.stage()) != 1) {
            throw new ServiceException("当前审批级别已发生变化，请刷新后重试");
        }
        closePendingAssignments(locked.stage().getStageInstanceId(), "RESTARTED", now);
        WorkflowApprovalV2Factory.Activation activation = approvalV2Factory.activateReplacement(
                locked.instance(), locked.stage().getSequenceNo() - 1,
                locked.execution().getPrincipalId(), command.targets());
        activation.stage().setStageKey(restartStageKey(locked.stage().getStageKey()));
        if (activation.configurationError()) {
            throw new ServiceException("重新发起后没有可用审批人，请重新选择");
        }
        if (approvalStageMapper.insert(activation.stage()) != 1) {
            throw new ServiceException("重新发起审批级别失败");
        }
        for (WorkflowApprovalAssignment assignment : activation.assignments()) {
            if (approvalAssignmentMapper.insert(assignment) != 1) {
                throw new ServiceException("重新发起审批人员失败");
            }
        }
        locked.instance().setCurrentStageId(activation.stage().getStageInstanceId());
        locked.instance().setStatus("PENDING");
        locked.instance().setReminderSentTime(null);
        locked.instance().setReminderTime(reminderAt(
                config.path("reminder"),
                earliest(locked.instance().getDeadline(), activation.stage().getDeadline()), now));
        locked.instance().setFinishTime(null);
        locked.instance().setUpdateTime(now);
        if (approvalInstanceMapper.updateById(locked.instance()) != 1) {
            throw new ServiceException("审批任务已被其他人更新，请刷新后重试");
        }
        auditManagementAction(locked, "APPROVAL_STAGE_RESTARTED",
                command.reason(), principals);
        return view(locked.instance(), activation.stage(), CallerUtils.getContext());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowApprovalTaskView remind(
            String approvalInstanceId, WorkflowApprovalRemindCommand command) {
        if (approvalInstanceId == null || approvalInstanceId.isBlank()) {
            throw new ServiceException("审批任务不能为空");
        }
        WorkflowApprovalInstance preview = approvalInstanceMapper.selectByInstanceId(
                approvalInstanceId);
        if (preview == null || !Objects.equals(preview.getTenantId(), currentTenantId())) {
            throw new ServiceException("审批任务不存在或无权访问");
        }
        CallerContext caller = CallerUtils.getContext();
        boolean admin = caller.isSuperAdmin() || caller.hasPermission(WorkflowPermission.ADMIN);
        if (!admin && !assignedToInstance(preview, actorId(caller))) {
            throw new ServiceException("当前用户无权催办该审批");
        }
        LockedApproval locked = lockPendingApproval(approvalInstanceId,
                command == null ? null : command.expectedLockVersion());
        String message = command == null || command.message() == null
                ? "请及时处理当前审批任务" : sanitizeComment(command.message());
        emitReminder(locked.instance(), locked.stage(), message,
                command == null ? null : command.requestId(), safeActorName(), false);
        return view(locked.instance(), locked.stage(), caller);
    }

    private WorkflowApprovalTaskView decideInstance(
            WorkflowApprovalInstance preview,
            WorkflowApprovalDecisionCommand command,
            String decision) {
        Long tenantId = currentTenantId();
        if (!Objects.equals(preview.getTenantId(), tenantId)) {
            throw new ServiceException("审批任务不存在或无权访问");
        }
        CallerContext caller = CallerUtils.getContext();
        String actorId = actorId(caller);
        WorkflowExecution execution = executionMapper.selectByExecutionIdForUpdate(
                preview.getExecutionId());
        WorkflowApprovalInstance instance = approvalInstanceMapper.selectByInstanceIdForUpdate(
                preview.getApprovalInstanceId());
        if (execution == null || instance == null || !"PENDING".equals(instance.getStatus())) {
            throw new ServiceException("审批任务已结束或不存在");
        }
        if (command.expectedLockVersion() != null
                && !command.expectedLockVersion().equals(instance.getLockVersion())) {
            throw new ServiceException("审批任务已被其他人更新，请刷新后重试");
        }
        WorkflowApprovalStage stage = approvalStageMapper.selectByStageIdForUpdate(
                instance.getCurrentStageId());
        if (stage == null || !"ACTIVE".equals(stage.getStatus())) {
            throw new ServiceException("当前审批级别不可处理");
        }
        Date now = new Date();
        if (expired(instance, stage, now)) {
            finishInstance(instance, stage, execution, "EXPIRED",
                    WorkflowErrorCode.APPROVAL_EXPIRED, "工作流审批已超时", now);
            return view(instance, stage, caller);
        }
        WorkflowApprovalAssignment assignment =
                approvalAssignmentMapper.selectByStageAndUserForUpdate(
                        stage.getStageInstanceId(), actorId);
        if (assignment == null || !"PENDING".equals(assignment.getStatus())) {
            WorkflowApprovalDecision existing = approvalDecisionMapper.selectByStageAndActor(
                    stage.getStageInstanceId(), actorId);
            if (existing != null) return view(instance, stage, caller);
            throw new ServiceException("当前用户不是本级审批人，或已经处理过");
        }
        JsonNode options = readJson(instance.getConfigSnapshot()).path("options");
        String comment = command.comment() == null ? "" : sanitizeComment(command.comment());
        if (("APPROVE".equals(decision)
                && options.path("requireApproveComment").asBoolean(false)
                || "REJECT".equals(decision)
                && options.path("requireRejectComment").asBoolean(true))
                && comment.isBlank()) {
            throw new ServiceException("请填写审批意见");
        }
        String requestId = command.requestId() == null || command.requestId().isBlank()
                ? UUID.randomUUID().toString() : command.requestId().trim();
        if (requestId.length() > 64) throw new ServiceException("审批请求ID过长");
        WorkflowApprovalDecision sameRequest = approvalDecisionMapper.selectByRequestId(requestId);
        if (sameRequest != null) {
            if (instance.getApprovalInstanceId().equals(sameRequest.getApprovalInstanceId())
                    && actorId.equals(sameRequest.getActorId())) return view(instance, stage, caller);
            throw new ServiceException("审批请求ID已被使用");
        }
        WorkflowApprovalDecision record = new WorkflowApprovalDecision();
        record.setTenantId(instance.getTenantId());
        record.setDecisionId(UUID.randomUUID().toString());
        record.setRequestId(requestId);
        record.setApprovalInstanceId(instance.getApprovalInstanceId());
        record.setStageInstanceId(stage.getStageInstanceId());
        record.setAssignmentId(assignment.getAssignmentId());
        record.setActorId(actorId);
        record.setActorName(safeActorName());
        record.setDecision(decision);
        record.setComment(comment.isBlank() ? null : comment);
        record.setCreateTime(now);
        if (approvalDecisionMapper.insert(record) != 1) {
            throw new ServiceException("保存审批决定失败");
        }
        assignment.setStatus("APPROVE".equals(decision) ? "APPROVED" : "REJECTED");
        assignment.setUpdateTime(now);
        if (approvalAssignmentMapper.updateById(assignment) != 1) {
            throw new ServiceException("审批资格已被其他人更新，请刷新后重试");
        }
        List<WorkflowApprovalAssignment> assignments =
                approvalAssignmentMapper.selectByStageId(stage.getStageInstanceId());
        int approved = countAssignments(assignments, "APPROVED");
        int rejected = countAssignments(assignments, "REJECTED");
        int pending = countAssignments(assignments, "PENDING");
        stage.setApprovedCount(approved);
        stage.setRejectedCount(rejected);
        stage.setPendingCount(pending);
        stage.setUpdateTime(now);
        JsonNode policy = readJson(stage.getPolicySnapshot());
        WorkflowApprovalDecisionEngine.Outcome outcome = decisionEngine.evaluate(
                policy.path("mode").asText("ANY"), stage.getRequiredApprovals(),
                policy.path("rejectOnAny").asBoolean(false), approved, rejected, pending);
        persistence.appendEvent(execution.getExecutionId(), null, null,
                "APPROVAL_DECIDED", instance.getNodeRunId(), null,
                Map.of("approvalInstanceId", instance.getApprovalInstanceId(),
                        "stageInstanceId", stage.getStageInstanceId(), "decision", decision));
        if (outcome == WorkflowApprovalDecisionEngine.Outcome.PENDING) {
            if (approvalStageMapper.updateById(stage) != 1) {
                throw new ServiceException("审批级别已被其他人更新，请刷新后重试");
            }
            return view(instance, stage, caller);
        }
        if (outcome == WorkflowApprovalDecisionEngine.Outcome.CONFIG_ERROR) {
            finishConfigError(instance, stage, execution, now);
            return view(instance, stage, caller);
        }
        if (outcome == WorkflowApprovalDecisionEngine.Outcome.REJECTED) {
            finishInstance(instance, stage, execution, "REJECTED",
                    WorkflowErrorCode.APPROVAL_REJECTED, "工作流审批被拒绝", now);
            return view(instance, stage, caller);
        }
        stage.setStatus("APPROVED");
        stage.setPendingCount(0);
        stage.setFinishTime(now);
        if (approvalStageMapper.updateById(stage) != 1) {
            throw new ServiceException("审批级别已被其他人更新，请刷新后重试");
        }
        closePendingAssignments(stage.getStageInstanceId(), "APPROVED", now);
        int nextIndex = stage.getSequenceNo();
        if (nextIndex < approvalV2Factory.stageCount(instance)) {
            WorkflowApprovalV2Factory.Activation activation = approvalV2Factory.activateNext(
                    instance, nextIndex, execution.getPrincipalId());
            if (approvalStageMapper.insert(activation.stage()) != 1) {
                throw new ServiceException("创建下一级审批失败");
            }
            for (WorkflowApprovalAssignment next : activation.assignments()) {
                if (approvalAssignmentMapper.insert(next) != 1) {
                    throw new ServiceException("创建下一级审批人员失败");
                }
            }
            instance.setCurrentStageId(activation.stage().getStageInstanceId());
            instance.setCurrentStageSequence(nextIndex + 1);
            JsonNode config = readJson(instance.getConfigSnapshot());
            instance.setReminderSentTime(null);
            instance.setReminderTime(reminderAt(
                    config.path("reminder"),
                    earliest(instance.getDeadline(), activation.stage().getDeadline()), now));
            instance.setUpdateTime(now);
            if (activation.configurationError()) {
                finishConfigError(instance, activation.stage(), execution, now);
            } else if (approvalInstanceMapper.updateById(instance) != 1) {
                throw new ServiceException("推进下一级审批失败");
            }
            return view(instance, activation.stage(), caller);
        }
        instance.setStatus("APPROVED");
        instance.setFinishTime(now);
        instance.setUpdateTime(now);
        if (approvalInstanceMapper.updateById(instance) != 1) {
            throw new ServiceException("完成审批实例失败");
        }
        persistApprovalOutput(instance);
        requeueAfterApproval(instance);
        return view(instance, stage, caller);
    }

    private void requireAdminCommand(
            String approvalInstanceId,
            List<WorkflowApprovalTarget> targets,
            String reason) {
        requireEnabled();
        CallerContext caller = CallerUtils.getContext();
        if (!caller.isSuperAdmin() && !caller.hasPermission(WorkflowPermission.ADMIN)) {
            throw new ServiceException("当前用户没有审批管理权限");
        }
        if (approvalInstanceId == null || approvalInstanceId.isBlank()) {
            throw new ServiceException("审批任务不能为空");
        }
        if (targets == null || targets.isEmpty() || targets.size() > 20) {
            throw new ServiceException("请选择1到20组审批对象");
        }
        if (reason == null || sanitizeComment(reason).isBlank()) {
            throw new ServiceException("请填写操作原因");
        }
    }

    private LockedApproval lockPendingApproval(
            String approvalInstanceId, Integer expectedLockVersion) {
        WorkflowApprovalInstance preview = approvalInstanceMapper.selectByInstanceId(
                approvalInstanceId);
        if (preview == null || !Objects.equals(preview.getTenantId(), currentTenantId())) {
            throw new ServiceException("审批任务不存在或无权访问");
        }
        WorkflowExecution execution = executionMapper.selectByExecutionIdForUpdate(
                preview.getExecutionId());
        WorkflowApprovalInstance instance = approvalInstanceMapper.selectByInstanceIdForUpdate(
                approvalInstanceId);
        if (execution == null || instance == null || !"PENDING".equals(instance.getStatus())
                || !"WAITING_APPROVAL".equals(execution.getStatus())
                || Boolean.TRUE.equals(execution.getCancelRequested())) {
            throw new ServiceException("审批任务已结束或不再等待审批");
        }
        if (expectedLockVersion != null
                && !expectedLockVersion.equals(instance.getLockVersion())) {
            throw new ServiceException("审批任务已被其他人更新，请刷新后重试");
        }
        WorkflowApprovalStage stage = approvalStageMapper.selectByStageIdForUpdate(
                instance.getCurrentStageId());
        if (stage == null || !"ACTIVE".equals(stage.getStatus())) {
            throw new ServiceException("当前审批级别不可处理");
        }
        return new LockedApproval(instance, stage, execution);
    }

    private List<WorkflowApprovalPrincipal> resolvePrincipals(
            WorkflowApprovalInstance instance,
            WorkflowExecution execution,
            List<WorkflowApprovalTarget> targets) {
        List<WorkflowApprovalPrincipal> principals;
        try {
            principals = new ArrayList<>(directoryResolver.resolve(instance.getTenantId(), targets));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ServiceException("选择的审批对象当前无法解析，请重新选择");
        }
        JsonNode options = readJson(instance.getConfigSnapshot()).path("options");
        if (!options.path("allowSelfApproval").asBoolean(false)
                && execution.getPrincipalId() != null) {
            principals.removeIf(item -> execution.getPrincipalId().equals(item.userId()));
        }
        if (principals.isEmpty()) {
            throw new ServiceException("所选对象没有可用审批人，或只包含禁止自审的发起人");
        }
        if (principals.size() > 500) {
            throw new ServiceException("单级审批人数不能超过500人");
        }
        return principals;
    }

    private void refreshStageCounts(WorkflowApprovalStage stage) {
        List<WorkflowApprovalAssignment> current = approvalAssignmentMapper.selectByStageId(
                stage.getStageInstanceId());
        stage.setApprovedCount(countAssignments(current, "APPROVED"));
        stage.setRejectedCount(countAssignments(current, "REJECTED"));
        stage.setPendingCount(countAssignments(current, "PENDING"));
        stage.setUpdateTime(new Date());
        if (approvalStageMapper.updateById(stage) != 1) {
            throw new ServiceException("审批级别已被其他人更新，请刷新后重试");
        }
    }

    private void ensureReachable(WorkflowApprovalStage stage) {
        JsonNode policy = readJson(stage.getPolicySnapshot());
        WorkflowApprovalDecisionEngine.Outcome outcome = decisionEngine.evaluate(
                policy.path("mode").asText("ANY"), stage.getRequiredApprovals(),
                policy.path("rejectOnAny").asBoolean(false),
                value(stage.getApprovedCount()), value(stage.getRejectedCount()),
                value(stage.getPendingCount()));
        if (outcome == WorkflowApprovalDecisionEngine.Outcome.CONFIG_ERROR
                || outcome == WorkflowApprovalDecisionEngine.Outcome.REJECTED) {
            throw new ServiceException("重新指派后剩余审批人无法满足当前通过规则，请增加审批人");
        }
    }

    private String restartStageKey(String original) {
        String suffix = "__retry_" + Long.toString(System.currentTimeMillis(), 36);
        String base = original == null || original.isBlank() ? "stage" : original;
        return base.substring(0, Math.min(base.length(), 64 - suffix.length())) + suffix;
    }

    private void auditManagementAction(
            LockedApproval locked,
            String eventType,
            String reason,
            List<WorkflowApprovalPrincipal> principals) {
        persistence.appendEvent(locked.execution().getExecutionId(), null, null,
                eventType, locked.instance().getNodeRunId(), null,
                Map.of("approvalInstanceId", locked.instance().getApprovalInstanceId(),
                        "stageInstanceId", locked.stage().getStageInstanceId(),
                        "operator", safeActorName(),
                        "reason", sanitizeComment(reason),
                        "assigneeCount", principals.size()));
    }

    private void emitReminder(
            WorkflowApprovalInstance instance,
            WorkflowApprovalStage stage,
            String message,
            String requestId,
            String actor,
            boolean automatic) {
        List<String> recipients = approvalAssignmentMapper.selectByStageId(
                        stage.getStageInstanceId()).stream()
                .filter(item -> "PENDING".equals(item.getStatus()))
                .map(WorkflowApprovalAssignment::getUserId).distinct().toList();
        if (recipients.isEmpty()) return;
        String safeRequestId = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString() : requestId.trim();
        if (safeRequestId.length() > 64) throw new ServiceException("催办请求ID过长");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approvalInstanceId", instance.getApprovalInstanceId());
        payload.put("stageInstanceId", stage.getStageInstanceId());
        payload.put("recipients", recipients);
        payload.put("message", message);
        payload.put("requestId", safeRequestId);
        payload.put("actor", actor);
        payload.put("automatic", automatic);
        persistence.appendEvent(instance.getExecutionId(), null, null,
                automatic ? "APPROVAL_REMINDER_DUE" : "APPROVAL_REMINDED",
                instance.getNodeRunId(), null, payload);
        persistence.appendOutboxEvent(instance.getTenantId(), "WORKFLOW_APPROVAL",
                instance.getApprovalInstanceId(), "WORKFLOW_APPROVAL_REMINDER", payload);
        if (automatic) {
            instance.setReminderSentTime(new Date());
            instance.setUpdateTime(new Date());
            if (approvalInstanceMapper.updateById(instance) != 1) {
                throw new ServiceException("审批提醒状态更新冲突");
            }
        }
    }

    private record LockedApproval(
            WorkflowApprovalInstance instance,
            WorkflowApprovalStage stage,
            WorkflowExecution execution) {
    }

    private List<WorkflowApprovalTaskView> listInstances(String status, CallerContext caller) {
        Long tenantId = currentTenantId();
        LambdaQueryWrapper<WorkflowApprovalInstance> query =
                new LambdaQueryWrapper<WorkflowApprovalInstance>()
                        .orderByAsc(WorkflowApprovalInstance::getDeadline)
                        .orderByDesc(WorkflowApprovalInstance::getCreateTime)
                        .last("LIMIT 200");
        if (tenantId == null) query.isNull(WorkflowApprovalInstance::getTenantId);
        else query.eq(WorkflowApprovalInstance::getTenantId, tenantId);
        if (status != null) query.eq(WorkflowApprovalInstance::getStatus, status);
        List<WorkflowApprovalInstance> instances = approvalInstanceMapper.selectList(query);
        if (instances.isEmpty()) return List.of();
        String userId = caller.getUserId() == null ? null : String.valueOf(caller.getUserId());
        List<String> instanceIds = instances.stream()
                .map(WorkflowApprovalInstance::getApprovalInstanceId).toList();
        List<WorkflowApprovalStage> stages = approvalStageMapper.selectList(
                new LambdaQueryWrapper<WorkflowApprovalStage>()
                        .in(WorkflowApprovalStage::getApprovalInstanceId, instanceIds)
                        .orderByAsc(WorkflowApprovalStage::getSequenceNo));
        List<WorkflowApprovalAssignment> assignments = userId == null ? List.of()
                : approvalAssignmentMapper.selectList(
                new LambdaQueryWrapper<WorkflowApprovalAssignment>()
                        .in(WorkflowApprovalAssignment::getApprovalInstanceId, instanceIds)
                        .eq(WorkflowApprovalAssignment::getUserId, userId));
        List<WorkflowApprovalDecision> decisions = userId == null ? List.of()
                : approvalDecisionMapper.selectList(
                new LambdaQueryWrapper<WorkflowApprovalDecision>()
                        .in(WorkflowApprovalDecision::getApprovalInstanceId, instanceIds)
                        .eq(WorkflowApprovalDecision::getActorId, userId));
        Map<String, List<WorkflowApprovalStage>> stagesByInstance = stages.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        WorkflowApprovalStage::getApprovalInstanceId));
        Map<String, List<WorkflowApprovalAssignment>> assignmentsByInstance = assignments.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        WorkflowApprovalAssignment::getApprovalInstanceId));
        Map<String, List<WorkflowApprovalDecision>> decisionsByInstance = decisions.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        WorkflowApprovalDecision::getApprovalInstanceId));
        return instances.stream()
                .filter(instance -> caller.isSuperAdmin()
                        || caller.hasPermission(WorkflowPermission.ADMIN)
                        || assignmentsByInstance.containsKey(instance.getApprovalInstanceId()))
                .map(instance -> {
                    String id = instance.getApprovalInstanceId();
                    List<WorkflowApprovalStage> instanceStages = stagesByInstance.getOrDefault(
                            id, List.of());
                    WorkflowApprovalStage stage = instanceStages.stream()
                            .filter(item -> Objects.equals(item.getStageInstanceId(),
                                    instance.getCurrentStageId()))
                            .findFirst().orElse(instanceStages.isEmpty()
                                    ? null : instanceStages.get(instanceStages.size() - 1));
                    return view(instance, stage, caller, false,
                            assignmentsByInstance.getOrDefault(id, List.of()),
                            instanceStages,
                            decisionsByInstance.getOrDefault(id, List.of()));
                })
                .toList();
    }

    private boolean assignedToInstance(WorkflowApprovalInstance instance, String userId) {
        return approvalAssignmentMapper.selectCount(
                new LambdaQueryWrapper<WorkflowApprovalAssignment>()
                        .eq(WorkflowApprovalAssignment::getApprovalInstanceId,
                                instance.getApprovalInstanceId())
                        .eq(WorkflowApprovalAssignment::getUserId, userId)) > 0;
    }

    private WorkflowApprovalStage currentStage(WorkflowApprovalInstance instance) {
        List<WorkflowApprovalStage> stages = approvalStageMapper.selectByInstanceId(
                instance.getApprovalInstanceId());
        if (stages.isEmpty()) return null;
        return stages.stream()
                .filter(stage -> Objects.equals(
                        stage.getStageInstanceId(), instance.getCurrentStageId()))
                .findFirst().orElse(stages.get(stages.size() - 1));
    }

    private int countAssignments(
            List<WorkflowApprovalAssignment> assignments, String status) {
        return (int) assignments.stream()
                .filter(item -> status.equals(item.getStatus())).count();
    }

    private WorkflowApprovalAssignment repairAssignment(
            WorkflowApprovalInstance instance,
            WorkflowApprovalStage stage,
            WorkflowApprovalPrincipal principal,
            Date now) {
        WorkflowApprovalAssignment assignment = new WorkflowApprovalAssignment();
        assignment.setTenantId(instance.getTenantId());
        assignment.setAssignmentId(UUID.randomUUID().toString());
        assignment.setApprovalInstanceId(instance.getApprovalInstanceId());
        assignment.setStageInstanceId(stage.getStageInstanceId());
        assignment.setUserId(principal.userId());
        assignment.setUsername(principal.username());
        assignment.setDisplayName(principal.displayName());
        assignment.setDepartmentId(principal.departmentId());
        assignment.setDepartmentName(principal.departmentName());
        assignment.setSourceSnapshot(writeJson(principal.sources()));
        assignment.setStatus("PENDING");
        assignment.setCreateTime(now);
        assignment.setUpdateTime(now);
        return assignment;
    }

    private Date approvalDeadline(JsonNode config, Date now) {
        if (config == null || !config.isObject() || !config.has("duration")) return null;
        long duration = config.path("duration").asLong(0);
        if (duration < 1) return null;
        ChronoUnit unit = switch (config.path("unit").asText("HOUR")) {
            case "MINUTE" -> ChronoUnit.MINUTES;
            case "DAY" -> ChronoUnit.DAYS;
            default -> ChronoUnit.HOURS;
        };
        try {
            Duration value = Duration.of(duration, unit);
            if (value.compareTo(Duration.ofMinutes(1)) < 0
                    || value.compareTo(Duration.ofDays(365)) > 0) {
                throw new ServiceException("审批期限必须在1分钟到365天之间");
            }
            if ("BUSINESS_DAY".equals(config.path("calendar").asText())
                    && "DAY".equals(config.path("unit").asText("HOUR"))) {
                String configuredZone = config.path("timezone").asText("TENANT");
                ZoneId zone = configuredZone.isBlank() || "TENANT".equals(configuredZone)
                        ? ZoneId.systemDefault() : ZoneId.of(configuredZone);
                ZonedDateTime time = now.toInstant().atZone(zone);
                long remaining = duration;
                while (remaining > 0) {
                    time = time.plusDays(1);
                    if (time.getDayOfWeek() != DayOfWeek.SATURDAY
                            && time.getDayOfWeek() != DayOfWeek.SUNDAY) {
                        remaining--;
                    }
                }
                return Date.from(time.toInstant());
            }
            return Date.from(now.toInstant().plus(value));
        } catch (RuntimeException exception) {
            throw new ServiceException("审批期限配置无效");
        }
    }

    private Date earliest(Date first, Date second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.before(second) ? first : second;
    }

    private boolean expired(
            WorkflowApprovalInstance instance,
            WorkflowApprovalStage stage,
            Date now) {
        return instance.getDeadline() != null && !instance.getDeadline().after(now)
                || stage.getDeadline() != null && !stage.getDeadline().after(now);
    }

    private void finishInstance(
            WorkflowApprovalInstance instance,
            WorkflowApprovalStage stage,
            WorkflowExecution execution,
            String status,
            WorkflowErrorCode errorCode,
            String message,
            Date now) {
        stage.setStatus(status);
        stage.setPendingCount(0);
        stage.setFinishTime(now);
        stage.setUpdateTime(now);
        if (approvalStageMapper.updateById(stage) != 1) {
            throw new ServiceException("审批级别状态更新冲突");
        }
        closePendingAssignments(stage.getStageInstanceId(), status, now);
        instance.setStatus(status);
        instance.setFinishTime(now);
        instance.setUpdateTime(now);
        if (approvalInstanceMapper.updateById(instance) != 1) {
            throw new ServiceException("审批实例状态更新冲突");
        }
        persistence.appendEvent(execution.getExecutionId(), null, null,
                "APPROVAL_" + status, instance.getNodeRunId(), null,
                Map.of("approvalInstanceId", instance.getApprovalInstanceId(),
                        "stageInstanceId", stage.getStageInstanceId()));
        if ("BRANCH".equals(instance.getResultMode())) {
            persistApprovalOutput(instance);
            requeueAfterApproval(instance);
        } else {
            rejectApprovalExecution(instance, execution, errorCode, message);
        }
    }

    private void finishConfigError(
            WorkflowApprovalInstance instance,
            WorkflowApprovalStage stage,
            WorkflowExecution execution,
            Date now) {
        if (!"CONFIG_ERROR".equals(stage.getStatus())) {
            stage.setStatus("CONFIG_ERROR");
            stage.setFinishTime(now);
            stage.setUpdateTime(now);
            if (approvalStageMapper.updateById(stage) != 1) {
                throw new ServiceException("审批配置异常状态更新冲突");
            }
        }
        instance.setStatus("CONFIG_ERROR");
        instance.setCurrentStageId(stage.getStageInstanceId());
        instance.setCurrentStageSequence(stage.getSequenceNo());
        instance.setFinishTime(now);
        instance.setUpdateTime(now);
        if (approvalInstanceMapper.updateById(instance) != 1) {
            throw new ServiceException("审批配置异常状态更新冲突");
        }
        WorkflowNodeRun run = waitingRun(instance);
        if (run != null) {
            run.setStatus("NEEDS_ATTENTION");
            run.setErrorCode("APPROVAL_CONFIG_ERROR");
            run.setErrorMessage("审批级别没有有效审批人，或通过人数配置无法满足");
            nodeRunMapper.updateById(run);
        }
        execution.setStatus("NEEDS_ATTENTION");
        execution.setRunnerId(null);
        execution.setLeaseUntil(null);
        execution.setHeartbeatTime(now);
        if (executionMapper.updateById(execution) != 1) {
            throw new ServiceException("工作流审批配置异常状态更新失败");
        }
        persistence.appendEvent(execution.getExecutionId(), null, null,
                "APPROVAL_CONFIG_ERROR", instance.getNodeRunId(), null,
                Map.of("approvalInstanceId", instance.getApprovalInstanceId(),
                        "stageInstanceId", stage.getStageInstanceId()));
    }

    private void closePendingAssignments(String stageId, String reason, Date now) {
        for (WorkflowApprovalAssignment assignment
                : approvalAssignmentMapper.selectByStageId(stageId)) {
            if (!"PENDING".equals(assignment.getStatus())) continue;
            assignment.setStatus("EXPIRED".equals(reason) ? "EXPIRED" : "CANCELLED");
            assignment.setUpdateTime(now);
            approvalAssignmentMapper.updateById(assignment);
        }
    }

    private void requeueAfterApproval(WorkflowApprovalInstance instance) {
        if (executionMapper.requeueAfterApproval(instance.getExecutionId()) != 1) {
            throw new ServiceException("审批完成，但工作流已不在等待状态");
        }
        eventPublisher.publishEvent(WorkflowTaskSignal.APPROVAL_COMPLETED);
    }

    private void rejectApprovalExecution(
            WorkflowApprovalInstance instance,
            WorkflowExecution execution,
            WorkflowErrorCode errorCode,
            String message) {
        WorkflowNodeRun run = waitingRun(instance);
        if (run != null) {
            run.setStatus("SUCCEEDED");
            run.setOutputJson(writeJson(approvalOutput(instance)));
            run.setErrorCode(null);
            run.setErrorMessage(null);
            run.setFinishTime(new Date());
            if (nodeRunMapper.updateById(run) != 1) {
                throw new ServiceException("审批节点业务结果保存失败");
            }
        }
        if (executionMapper.rejectAfterApproval(
                instance.getExecutionId(), errorCode.name(), message) != 1) {
            throw new ServiceException("审批实例对应执行已不在等待状态");
        }
        quotaService.release(execution);
        persistence.appendEvent(instance.getExecutionId(), null, null,
                "EXECUTION_REJECTED", instance.getNodeRunId(), null,
                Map.of("approvalInstanceId", instance.getApprovalInstanceId(),
                        "errorCode", errorCode.name()));
    }

    private ObjectNode approvalOutput(WorkflowApprovalInstance instance) {
        List<WorkflowApprovalStage> stages = approvalStageMapper.selectByInstanceId(
                instance.getApprovalInstanceId());
        int approved = stages.stream().mapToInt(item -> value(item.getApprovedCount())).sum();
        int rejected = stages.stream().mapToInt(item -> value(item.getRejectedCount())).sum();
        int completed = (int) stages.stream().filter(item -> Set.of(
                "APPROVED", "REJECTED", "EXPIRED", "SUPERSEDED").contains(item.getStatus())).count();
        int total = approvalV2Factory.stageCount(instance);
        ObjectNode output = objectMapper.createObjectNode();
        output.put("status", instance.getStatus());
        output.put("approvalInstanceId", instance.getApprovalInstanceId());
        output.put("approvedCount", approved);
        output.put("rejectedCount", rejected);
        output.put("stageCount", total);
        output.put("startedAt", instance.getCreateTime() == null
                ? System.currentTimeMillis() : instance.getCreateTime().getTime());
        output.put("completedStageCount", Math.min(completed, total));
        output.put("totalStageCount", total);
        ObjectNode summary = output.putObject("decisionSummary");
        summary.put("approvedCount", approved);
        summary.put("rejectedCount", rejected);
        summary.put("completedStageCount", Math.min(completed, total));
        summary.put("totalStageCount", total);
        List<WorkflowApprovalDecision> decisions = approvalDecisionMapper.selectByInstanceId(
                instance.getApprovalInstanceId());
        if (!decisions.isEmpty()) {
            WorkflowApprovalDecision last = decisions.get(decisions.size() - 1);
            if (last.getActorName() != null && !last.getActorName().isBlank()) {
                output.put("finalActor", last.getActorName());
            }
        }
        output.put("finishedAt", instance.getFinishTime() == null
                ? System.currentTimeMillis() : instance.getFinishTime().getTime());
        return output;
    }

    private void persistApprovalOutput(WorkflowApprovalInstance instance) {
        WorkflowNodeRun run = waitingRun(instance);
        if (run == null) return;
        run.setOutputJson(writeJson(approvalOutput(instance)));
        run.setUpdateTime(new Date());
        if (nodeRunMapper.updateById(run) != 1) {
            throw new ServiceException("审批节点结果保存失败");
        }
    }

    private WorkflowNodeRun waitingRun(WorkflowApprovalInstance instance) {
        return nodeRunMapper.selectOne(
                new LambdaQueryWrapper<WorkflowNodeRun>()
                        .eq(WorkflowNodeRun::getExecutionId, instance.getExecutionId())
                        .eq(WorkflowNodeRun::getNodeRunId, instance.getNodeRunId())
                        .eq(WorkflowNodeRun::getStatus, "WAITING")
                        .last("LIMIT 1"));
    }

    private void expireInstances() {
        Date now = new Date();
        List<WorkflowApprovalInstance> instances =
                approvalInstanceMapper.selectExpiredCandidates(now, 200);
        for (WorkflowApprovalInstance candidate : instances) {
            WorkflowApprovalStage previewStage = currentStage(candidate);
            if (previewStage == null || !expired(candidate, previewStage, now)) continue;
            WorkflowExecution execution = executionMapper.selectByExecutionIdForUpdate(
                    candidate.getExecutionId());
            WorkflowApprovalInstance instance = approvalInstanceMapper
                    .selectByInstanceIdForUpdate(candidate.getApprovalInstanceId());
            if (execution == null || instance == null || !"PENDING".equals(instance.getStatus())) {
                continue;
            }
            WorkflowApprovalStage stage = approvalStageMapper.selectByStageIdForUpdate(
                    instance.getCurrentStageId());
            if (stage != null && "ACTIVE".equals(stage.getStatus())
                    && expired(instance, stage, new Date())) {
                if (escalateExpired(instance, stage, execution, new Date())) {
                    continue;
                }
                finishInstance(instance, stage, execution, "EXPIRED",
                        WorkflowErrorCode.APPROVAL_EXPIRED, "工作流审批已超时", new Date());
            }
        }
    }

    private void sendDueReminders() {
        Date now = new Date();
        for (WorkflowApprovalInstance candidate
                : approvalInstanceMapper.selectReminderCandidates(now, 200)) {
            WorkflowApprovalInstance instance = approvalInstanceMapper
                    .selectByInstanceIdForUpdate(candidate.getApprovalInstanceId());
            if (instance == null || !"PENDING".equals(instance.getStatus())
                    || instance.getReminderSentTime() != null) continue;
            WorkflowApprovalStage stage = approvalStageMapper.selectByStageIdForUpdate(
                    instance.getCurrentStageId());
            if (stage == null || !"ACTIVE".equals(stage.getStatus())) continue;
            emitReminder(instance, stage, "审批即将到期，请及时处理",
                    null, "system", true);
        }
    }

    private boolean escalateExpired(
            WorkflowApprovalInstance instance,
            WorkflowApprovalStage stage,
            WorkflowExecution execution,
            Date now) {
        JsonNode config = readJson(instance.getConfigSnapshot());
        JsonNode policy = config.path("expirationPolicy");
        if (!"REASSIGN".equals(policy.path("action").asText())) return false;
        int maximum = policy.path("maxEscalations").asInt(1);
        int current = value(instance.getEscalationCount());
        if (current >= maximum || !policy.path("targets").isArray()
                || policy.path("targets").isEmpty()) return false;
        List<WorkflowApprovalTarget> targets = new ArrayList<>();
        for (JsonNode target : policy.path("targets")) {
            List<String> ids = new ArrayList<>();
            target.path("ids").forEach(id -> ids.add(id.asText()));
            targets.add(new WorkflowApprovalTarget(target.path("type").asText(), ids,
                    target.path("includeChildren").asBoolean(false)));
        }
        List<WorkflowApprovalPrincipal> principals;
        try {
            principals = resolvePrincipals(instance, execution, targets);
        } catch (ServiceException exception) {
            return false;
        }
        stage.setStatus("EXPIRED");
        stage.setFinishTime(now);
        stage.setUpdateTime(now);
        if (approvalStageMapper.updateById(stage) != 1) return false;
        closePendingAssignments(stage.getStageInstanceId(), "EXPIRED", now);
        instance.setDeadline(approvalDeadline(config.path("deadline"), now));
        WorkflowApprovalV2Factory.Activation activation = approvalV2Factory.activateReplacement(
                instance, stage.getSequenceNo() - 1, execution.getPrincipalId(), targets);
        if (activation.configurationError()) return false;
        activation.stage().setStageKey(restartStageKey(stage.getStageKey()));
        if (approvalStageMapper.insert(activation.stage()) != 1) {
            throw new ServiceException("创建超时转交审批级别失败");
        }
        for (WorkflowApprovalAssignment assignment : activation.assignments()) {
            if (approvalAssignmentMapper.insert(assignment) != 1) {
                throw new ServiceException("创建超时转交审批人员失败");
            }
        }
        instance.setCurrentStageId(activation.stage().getStageInstanceId());
        instance.setEscalationCount(current + 1);
        instance.setReminderSentTime(null);
        instance.setReminderTime(reminderAt(
                config.path("reminder"),
                earliest(instance.getDeadline(), activation.stage().getDeadline()), now));
        instance.setUpdateTime(now);
        if (approvalInstanceMapper.updateById(instance) != 1) {
            throw new ServiceException("审批超时转交状态更新冲突");
        }
        Map<String, Object> payload = Map.of(
                "approvalInstanceId", instance.getApprovalInstanceId(),
                "fromStageInstanceId", stage.getStageInstanceId(),
                "toStageInstanceId", activation.stage().getStageInstanceId(),
                "assigneeCount", principals.size(),
                "escalationCount", current + 1);
        persistence.appendEvent(execution.getExecutionId(), null, null,
                "APPROVAL_ESCALATED", instance.getNodeRunId(), null, payload);
        persistence.appendOutboxEvent(instance.getTenantId(), "WORKFLOW_APPROVAL",
                instance.getApprovalInstanceId(), "WORKFLOW_APPROVAL_ESCALATED", payload);
        return true;
    }

    private Date reminderAt(JsonNode reminder, Date deadline, Date now) {
        if (deadline == null || !reminder.path("enabled").asBoolean(false)) return null;
        long duration = reminder.path("beforeDuration").asLong(0);
        if (duration < 1) return null;
        ChronoUnit unit = switch (reminder.path("beforeUnit").asText("HOUR")) {
            case "MINUTE" -> ChronoUnit.MINUTES;
            case "DAY" -> ChronoUnit.DAYS;
            default -> ChronoUnit.HOURS;
        };
        Date result = Date.from(deadline.toInstant().minus(Duration.of(duration, unit)));
        return result.before(now) ? now : result;
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

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(result)) throw new ServiceException("审批状态无效");
        return result;
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new ServiceException("工作流 尚未启用");
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

    private WorkflowApprovalTaskView view(
            WorkflowApprovalInstance instance,
            WorkflowApprovalStage stage,
            CallerContext caller) {
        return view(instance, stage, caller, true,
                stage == null ? List.of()
                        : approvalAssignmentMapper.selectByStageId(stage.getStageInstanceId()),
                approvalStageMapper.selectByInstanceId(instance.getApprovalInstanceId()),
                approvalDecisionMapper.selectByInstanceId(instance.getApprovalInstanceId()));
    }

    private WorkflowApprovalTaskView view(
            WorkflowApprovalInstance instance,
            WorkflowApprovalStage stage,
            CallerContext caller,
            boolean includeDetails,
            List<WorkflowApprovalAssignment> assignments,
            List<WorkflowApprovalStage> stages,
            List<WorkflowApprovalDecision> decisions) {
        JsonNode config = readJson(instance.getConfigSnapshot());
        JsonNode policy = stage == null
                ? objectMapper.createObjectNode() : readJson(stage.getPolicySnapshot());
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("configVersion", instance.getConfigVersion());
        summary.put("resultMode", instance.getResultMode());
        summary.put("currentStageSequence",
                instance.getCurrentStageSequence() == null ? 0
                        : instance.getCurrentStageSequence());
        summary.put("requireApproveComment", config.path("options")
                .path("requireApproveComment").asBoolean(false));
        summary.put("requireRejectComment", config.path("options")
                .path("requireRejectComment").asBoolean(true));
        summary.put("canRepair", "CONFIG_ERROR".equals(instance.getStatus())
                && (caller.isSuperAdmin()
                || caller.hasPermission(WorkflowPermission.ADMIN)));
        boolean canManage = caller.isSuperAdmin()
                || caller.hasPermission(WorkflowPermission.ADMIN);
        summary.put("canManage", canManage);
        summary.put("canRemind", "PENDING".equals(instance.getStatus())
                && (canManage || assignments.stream().anyMatch(item -> caller.getUserId() != null
                && String.valueOf(caller.getUserId()).equals(item.getUserId()))));
        String currentActorId = caller.getUserId() == null
                ? null : String.valueOf(caller.getUserId());
        WorkflowApprovalAssignment currentAssignment = null;
        if (stage != null && currentActorId != null) {
            currentAssignment = assignments.stream()
                    .filter(item -> stage.getStageInstanceId().equals(item.getStageInstanceId()))
                    .filter(item -> currentActorId.equals(item.getUserId()))
                    .findFirst().orElse(null);
        }
        boolean canHandle = "PENDING".equals(instance.getStatus())
                && currentAssignment != null
                && "PENDING".equals(currentAssignment.getStatus());
        summary.put("canHandle", canHandle);
        if (currentAssignment != null && Set.of("APPROVED", "REJECTED")
                .contains(currentAssignment.getStatus())) {
            summary.put("currentUserDecision",
                    "APPROVED".equals(currentAssignment.getStatus()) ? "APPROVE" : "REJECT");
        }
        if (stage != null) {
            summary.put("stageInstanceId", stage.getStageInstanceId());
            summary.put("stageName", stage.getStageName());
            summary.put("approvedCount", value(stage.getApprovedCount()));
            summary.put("rejectedCount", value(stage.getRejectedCount()));
            summary.put("pendingCount", value(stage.getPendingCount()));
        }
        Map<String, String> stageNames = new HashMap<>();
        for (WorkflowApprovalStage item : stages) {
            stageNames.put(item.getStageInstanceId(), item.getStageName());
        }
        ArrayNode history = summary.putArray("history");
        for (WorkflowApprovalDecision decision : decisions) {
            if (includeDetails) {
                ObjectNode item = history.addObject();
                item.put("stageName", stageNames.getOrDefault(
                        decision.getStageInstanceId(), "审批"));
                item.put("actorName", decision.getActorName() == null
                        ? "审批人" : decision.getActorName());
                item.put("decision", decision.getDecision());
                if (decision.getComment() != null && !decision.getComment().isBlank()) {
                    item.put("comment", decision.getComment());
                }
                if (decision.getCreateTime() != null) {
                    item.put("decidedAt", decision.getCreateTime().getTime());
                }
            }
            if (currentActorId != null && currentActorId.equals(decision.getActorId())) {
                summary.put("currentUserDecision", decision.getDecision());
            }
        }
        String contentSnapshot = includeDetails
                ? instance.getContentSnapshot() : contentSummary(instance.getContentSnapshot());
        return new WorkflowApprovalTaskView(
                instance.getApprovalInstanceId(), instance.getExecutionId(),
                instance.getNodeRunId(),
                policy.path("mode").asText("ANY"),
                stage == null ? 1 : stage.getRequiredApprovals(),
                config.path("options").path("allowSelfApproval").asBoolean(false),
                instance.getStatus(), writeJson(summary),
                stage != null && stage.getDeadline() != null
                        ? stage.getDeadline() : instance.getDeadline(),
                instance.getLockVersion(), instance.getCreateTime(), instance.getFinishTime(),
                contentSnapshot, stage == null ? null : stage.getStageName(),
                instance.getCurrentStageSequence(), approvalV2Factory.stageCount(instance));
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }

    private String contentSummary(String snapshotJson) {
        JsonNode snapshot = readJson(snapshotJson);
        ObjectNode summary = objectMapper.createObjectNode();
        for (String name : List.of("title", "workflowCode", "nodeName",
                "initiatorName", "createdAt")) {
            if (snapshot.has(name)) summary.set(name, snapshot.get(name));
        }
        return writeJson(summary);
    }
}
