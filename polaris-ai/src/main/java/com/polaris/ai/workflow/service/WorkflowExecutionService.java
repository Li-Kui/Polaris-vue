package com.polaris.ai.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.workflow.application.*;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.definition.WorkflowExecutionPlan;
import com.polaris.ai.workflow.domain.*;
import com.polaris.ai.workflow.mapper.*;
import com.polaris.ai.workflow.runtime.WorkflowExecutionPersistence;
import com.polaris.ai.workflow.runtime.WorkflowInputValidator;
import com.polaris.ai.workflow.runtime.WorkflowQuotaService;
import com.polaris.ai.workflow.runtime.WorkflowResourceResolver;
import com.polaris.ai.workflow.security.WorkflowDataRedactor;
import com.polaris.common.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** 面向调用方的持久化执行创建、查询和取消服务。 */
@Service
public class WorkflowExecutionService implements WorkflowExecutionApplicationFacade {

    private static final int MAX_INPUT_BYTES = 1024 * 1024;
    private static final Set<String> ENVIRONMENTS = Set.of("DEV", "TEST", "PROD");

    private final WorkflowDefinitionMapper definitionMapper;
    private final WorkflowVersionMapper versionMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;
    private final WorkflowEventMapper eventMapper;
    private final WorkflowApprovalTaskMapper approvalTaskMapper;
    private final WorkflowExecutionPersistence persistence;
    private final WorkflowResourceResolver resourceResolver;
    private final WorkflowInputValidator inputValidator;
    private final WorkflowQuotaService quotaService;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;
    private final WorkflowDataRedactor dataRedactor;

    public WorkflowExecutionService(
            WorkflowDefinitionMapper definitionMapper,
            WorkflowVersionMapper versionMapper,
            WorkflowExecutionMapper executionMapper,
            WorkflowNodeRunMapper nodeRunMapper,
            WorkflowEventMapper eventMapper,
            WorkflowApprovalTaskMapper approvalTaskMapper,
            WorkflowExecutionPersistence persistence,
            WorkflowResourceResolver resourceResolver,
            WorkflowInputValidator inputValidator,
            WorkflowQuotaService quotaService,
            WorkflowProperties properties,
            ObjectMapper objectMapper,
            WorkflowDataRedactor dataRedactor) {
        this.definitionMapper = definitionMapper;
        this.versionMapper = versionMapper;
        this.executionMapper = executionMapper;
        this.nodeRunMapper = nodeRunMapper;
        this.eventMapper = eventMapper;
        this.approvalTaskMapper = approvalTaskMapper;
        this.persistence = persistence;
        this.resourceResolver = resourceResolver;
        this.inputValidator = inputValidator;
        this.quotaService = quotaService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.dataRedactor = dataRedactor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionView start(WorkflowExecutionStartCommand command) {
        requireStartEnabled();
        if (command == null || command.definitionId() == null) {
            throw new ServiceException("工作流定义ID不能为空");
        }
        WorkflowDefinition definition = definitionMapper.selectById(command.definitionId());
        if (definition == null || !"0".equals(definition.getDelFlag())) {
            throw new ServiceException("工作流定义不存在或无权访问");
        }
        return startDefinition(definition, command.workflowVersionId(), command.input(),
                command.environment(), command.idempotencyKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionView startByCode(WorkflowExecutionByCodeCommand command) {
        requireStartEnabled();
        if (command == null || command.workflowCode() == null
                || !command.workflowCode().matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
            throw new ServiceException("工作流编码格式无效");
        }
        Long tenantId = currentTenantId();
        LambdaQueryWrapper<WorkflowDefinition> definitionQuery =
                new LambdaQueryWrapper<WorkflowDefinition>()
                        .eq(WorkflowDefinition::getWorkflowCode, command.workflowCode())
                        .eq(WorkflowDefinition::getStatus, "ACTIVE")
                        .eq(WorkflowDefinition::getDelFlag, "0");
        if (tenantId == null) {
            definitionQuery.isNull(WorkflowDefinition::getTenantId);
        } else {
            definitionQuery.eq(WorkflowDefinition::getTenantId, tenantId);
        }
        WorkflowDefinition definition = definitionMapper.selectOne(
                definitionQuery.last("LIMIT 1"));
        if (definition == null) {
            throw new ServiceException("工作流不存在、未发布或无权访问");
        }
        return startDefinition(definition, null, command.input(),
                command.environment(), command.idempotencyKey());
    }

    private WorkflowExecutionView startDefinition(
            WorkflowDefinition definition,
            String requestedVersionId,
            JsonNode requestedInput,
            String requestedEnvironment,
            String requestedIdempotencyKey) {
        String versionId = requestedVersionId;
        if (versionId == null || versionId.isBlank()) {
            versionId = definition.getCurrentPublishedVersionId();
        }
        if (versionId == null || versionId.isBlank()) {
            throw new ServiceException("工作流尚未发布可执行版本");
        }
        WorkflowVersion version = versionMapper.selectByVersionId(versionId);
        if (version == null || !version.getDefinitionId().equals(definition.getId())
                || !"PUBLISHED".equals(version.getStatus())) {
            throw new ServiceException("工作流发布版本不存在、已退役或不属于当前定义");
        }
        WorkflowExecutionPlan plan = readPlan(version.getExecutionPlanJson());
        if (!version.getContentHash().equals(plan.getContentHash())) {
            throw new ServiceException("执行计划哈希不一致，拒绝运行");
        }
        if (!properties.isWriteNodesEnabled() && plan.getNodes().stream()
                .anyMatch(node -> "WRITE".equals(node.getSideEffect()))) {
            throw new ServiceException("当前环境未启用有副作用的工作流节点");
        }
        JsonNode input = requestedInput == null
                ? objectMapper.createObjectNode() : requestedInput;
        String inputJson = writeJson(input);
        if (inputJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw new ServiceException("工作流输入不能超过1MB");
        }
        List<String> inputErrors = inputValidator.validate(plan.getInputs(), input);
        if (!inputErrors.isEmpty()) {
            throw new ServiceException("工作流输入校验失败: " + String.join("; ", inputErrors));
        }
        String environment = normalizeEnvironment(requestedEnvironment);
        Principal principal = currentPrincipal(definition);
        if (principal.tenantId() != null
                && !properties.isEnabledForTenant(principal.tenantId())) {
            throw new ServiceException("目标租户尚未启用 工作流");
        }
        String idempotencyKey = normalizeIdempotencyKey(requestedIdempotencyKey);
        WorkflowExecution execution = createExecution(
                definition, version, plan, inputJson, environment, principal,
                idempotencyKey, writeJson(Map.of(
                        "type", principal.type(),
                        "id", principal.id(),
                        "tenantId", principal.tenantId() == null ? 0 : principal.tenantId(),
                        "username", CallerUtils.getUsername())), null);
        return view(execution);
    }

    /** 不依赖请求线程调用上下文，启动一个不可变版本的子工作流。 */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecution startChild(
            WorkflowExecution parent,
            String workflowCode,
            String workflowVersionId,
            JsonNode input,
            String nodeRunId) {
        if (parent == null || workflowCode == null || workflowCode.isBlank()
                || workflowVersionId == null || workflowVersionId.isBlank()) {
            throw new ServiceException("子工作流配置不完整");
        }
        int depth = parent.getExecutionDepth() == null ? 0 : parent.getExecutionDepth();
        if (depth >= 5) {
            throw new ServiceException("子工作流递归深度不能超过5层");
        }
        assertNoRecursiveWorkflow(parent, workflowCode);
        LambdaQueryWrapper<WorkflowDefinition> definitionQuery =
                new LambdaQueryWrapper<WorkflowDefinition>()
                        .eq(WorkflowDefinition::getWorkflowCode, workflowCode)
                        .eq(WorkflowDefinition::getStatus, "ACTIVE")
                        .eq(WorkflowDefinition::getDelFlag, "0");
        if (parent.getTenantId() == null) {
            definitionQuery.isNull(WorkflowDefinition::getTenantId);
        } else {
            definitionQuery.eq(WorkflowDefinition::getTenantId, parent.getTenantId());
        }
        WorkflowDefinition definition = definitionMapper.selectOne(
                definitionQuery.last("LIMIT 1"));
        if (definition == null) {
            throw new ServiceException("子工作流不存在、未启用或不属于当前租户");
        }
        WorkflowVersion version = versionMapper.selectByVersionId(workflowVersionId);
        if (version == null || !definition.getId().equals(version.getDefinitionId())
                || !"PUBLISHED".equals(version.getStatus())) {
            throw new ServiceException("子工作流不可变发布版本无效");
        }
        WorkflowExecutionPlan plan = readPlan(version.getExecutionPlanJson());
        if (!version.getContentHash().equals(plan.getContentHash())) {
            throw new ServiceException("子工作流执行计划哈希不一致");
        }
        if (!properties.isWriteNodesEnabled() && plan.getNodes().stream()
                .anyMatch(node -> "WRITE".equals(node.getSideEffect()))) {
            throw new ServiceException("当前环境未启用子工作流中的写节点");
        }
        JsonNode safeInput = input == null ? objectMapper.createObjectNode() : input;
        String inputJson = writeJson(safeInput);
        if (inputJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw new ServiceException("子工作流输入不能超过1MB");
        }
        List<String> inputErrors = inputValidator.validate(plan.getInputs(), safeInput);
        if (!inputErrors.isEmpty()) {
            throw new ServiceException("子工作流输入校验失败: " + String.join("; ", inputErrors));
        }
        Principal principal = new Principal(
                parent.getTenantId(), parent.getPrincipalType(), parent.getPrincipalId(),
                parent.getIdempotencyScope() + ":child");
        String idempotencyKey = nodeRunId.length() > 128
                ? nodeRunId.substring(0, 128) : nodeRunId;
        return createExecution(
                definition, version, plan, inputJson, parent.getEnvironment(), principal,
                idempotencyKey, parent.getPrincipalSnapshot(), parent);
    }

    private WorkflowExecution createExecution(
            WorkflowDefinition definition,
            WorkflowVersion version,
            WorkflowExecutionPlan plan,
            String inputJson,
            String environment,
            Principal principal,
            String idempotencyKey,
            String principalSnapshot,
            WorkflowExecution parent) {
        if (idempotencyKey != null) {
            WorkflowExecution existing = executionMapper.selectByIdempotency(
                    principal.scope(), idempotencyKey);
            if (existing != null) {
                ensureSameRequest(existing, definition.getId(), version.getVersionId(), inputJson);
                return existing;
            }
        }
        WorkflowResourceResolver.Resolution resources = resourceResolver.resolve(
                plan,
                definition.getId(),
                principal.tenantId(),
                environment,
                principal.type(),
                principal.id());
        WorkflowExecution execution = new WorkflowExecution();
        execution.setTenantId(principal.tenantId());
        execution.setExecutionId(UUID.randomUUID().toString());
        execution.setParentExecutionId(parent == null ? null : parent.getExecutionId());
        execution.setRootExecutionId(parent == null
                ? execution.getExecutionId()
                : parent.getRootExecutionId() == null
                ? parent.getExecutionId() : parent.getRootExecutionId());
        execution.setExecutionDepth(parent == null ? 0
                : (parent.getExecutionDepth() == null ? 0 : parent.getExecutionDepth()) + 1);
        execution.setDefinitionId(definition.getId());
        execution.setWorkflowVersionId(version.getVersionId());
        execution.setWorkflowCode(definition.getWorkflowCode());
        execution.setVersionNo(version.getVersionNo());
        execution.setPlanHash(version.getContentHash());
        execution.setPrincipalType(principal.type());
        execution.setPrincipalId(principal.id());
        execution.setIdempotencyScope(principal.scope());
        execution.setIdempotencyKey(idempotencyKey);
        execution.setPrincipalSnapshot(principalSnapshot);
        execution.setBindingSnapshot(resources.snapshotJson());
        execution.setEnvironment(environment);
        execution.setInputJson(inputJson);
        execution.setStatus("QUEUED");
        execution.setFencingToken(0L);
        execution.setRecoveryCount(0);
        execution.setCancelRequested(false);
        execution.setBudgetJson(writeJson(plan.getPolicies()));
        execution.setUsageJson("{}");
        execution.setQuotaReleased(parent != null);
        execution.setLockVersion(0);
        execution.setCreateTime(new Date());
        execution.setUpdateTime(new Date());
        try {
            persistence.createQueued(execution);
            if (parent == null) {
                quotaService.reserve(execution);
                if (executionMapper.updateById(execution) != 1) {
                    throw new ServiceException("保存工作流并发配额快照失败");
                }
            }
        } catch (DuplicateKeyException e) {
            if (idempotencyKey == null) {
                throw e;
            }
            WorkflowExecution existing = executionMapper.selectByIdempotency(
                    principal.scope(), idempotencyKey);
            if (existing == null) {
                throw e;
            }
            ensureSameRequest(existing, definition.getId(), version.getVersionId(), inputJson);
            return existing;
        }
        return execution;
    }

    @Override
    public WorkflowExecutionView get(String executionId) {
        requireEnabled();
        return view(requireExecution(executionId));
    }

    @Override
    public List<WorkflowExecutionView> list(Long definitionId, String status) {
        requireEnabled();
        LambdaQueryWrapper<WorkflowExecution> query = new LambdaQueryWrapper<>();
        applyTenantScope(query, currentTenantId());
        if (definitionId != null) {
            query.eq(WorkflowExecution::getDefinitionId, definitionId);
        }
        if (status != null && !status.isBlank()) {
            query.eq(WorkflowExecution::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        query.orderByDesc(WorkflowExecution::getCreateTime).last("LIMIT 200");
        return executionMapper.selectList(query).stream().map(this::view).toList();
    }

    @Override
    public List<WorkflowNodeRunView> listNodeRuns(String executionId) {
        requireEnabled();
        requireExecution(executionId);
        return nodeRunMapper.selectList(new LambdaQueryWrapper<WorkflowNodeRun>()
                        .eq(WorkflowNodeRun::getExecutionId, executionId)
                        .orderByAsc(WorkflowNodeRun::getId))
                .stream().map(this::nodeRunView).toList();
    }

    @Override
    public List<WorkflowExecutionEventView> listEvents(
            String executionId, long afterSequence, int limit) {
        requireEnabled();
        requireExecution(executionId);
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return eventMapper.selectList(new LambdaQueryWrapper<WorkflowEvent>()
                        .eq(WorkflowEvent::getExecutionId, executionId)
                        .gt(WorkflowEvent::getSequenceNo, Math.max(0, afterSequence))
                        .orderByAsc(WorkflowEvent::getSequenceNo)
                        .last("LIMIT " + safeLimit))
                .stream().map(this::eventView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionView cancel(String executionId) {
        requireEnabled();
        WorkflowExecution execution = requireExecution(executionId);
        if (isTerminal(execution.getStatus())) {
            return view(execution);
        }
        boolean cancelledImmediately = executionMapper.cancelBeforeRun(executionId) == 1
                || executionMapper.cancelWhileWaiting(executionId) == 1;
        if (cancelledImmediately) {
            quotaService.release(execution);
            approvalTaskMapper.update(null,
                    new LambdaUpdateWrapper<WorkflowApprovalTask>()
                            .eq(WorkflowApprovalTask::getExecutionId, executionId)
                            .eq(WorkflowApprovalTask::getStatus, "PENDING")
                            .set(WorkflowApprovalTask::getStatus, "CANCELLED")
                            .set(WorkflowApprovalTask::getFinishTime, new Date()));
            nodeRunMapper.update(null,
                    new LambdaUpdateWrapper<WorkflowNodeRun>()
                            .eq(WorkflowNodeRun::getExecutionId, executionId)
                            .eq(WorkflowNodeRun::getStatus, "WAITING")
                            .set(WorkflowNodeRun::getStatus, "CANCELLED")
                            .set(WorkflowNodeRun::getErrorCode,
                                    com.polaris.ai.workflow.contract.WorkflowErrorCode.NODE_CANCELLED.name())
                            .set(WorkflowNodeRun::getErrorMessage, "工作流等待期间被取消")
                            .set(WorkflowNodeRun::getFinishTime, new Date()));
            persistence.appendEvent(executionId, null, null,
                    "EXECUTION_CANCELLED", null, null, Map.of("immediate", true));
        } else {
            executionMapper.requestCancel(executionId);
        }
        cancelActiveDescendants(execution);
        return view(requireExecution(executionId));
    }

    private void cancelActiveDescendants(WorkflowExecution parent) {
        String rootExecutionId = parent.getRootExecutionId() == null
                ? parent.getExecutionId() : parent.getRootExecutionId();
        List<WorkflowExecution> descendants = executionMapper.selectList(
                new LambdaQueryWrapper<WorkflowExecution>()
                        .eq(WorkflowExecution::getRootExecutionId, rootExecutionId)
                        .ne(WorkflowExecution::getExecutionId, parent.getExecutionId())
                        .notIn(WorkflowExecution::getStatus,
                                "SUCCEEDED", "FAILED", "CANCELLED", "REJECTED"));
        for (WorkflowExecution child : descendants) {
            boolean immediate = executionMapper.cancelBeforeRun(child.getExecutionId()) == 1
                    || executionMapper.cancelWhileWaiting(child.getExecutionId()) == 1;
            if (immediate) {
                quotaService.release(child);
                approvalTaskMapper.update(null,
                        new LambdaUpdateWrapper<WorkflowApprovalTask>()
                                .eq(WorkflowApprovalTask::getExecutionId, child.getExecutionId())
                                .eq(WorkflowApprovalTask::getStatus, "PENDING")
                                .set(WorkflowApprovalTask::getStatus, "CANCELLED")
                                .set(WorkflowApprovalTask::getFinishTime, new Date()));
                nodeRunMapper.update(null,
                        new LambdaUpdateWrapper<WorkflowNodeRun>()
                                .eq(WorkflowNodeRun::getExecutionId, child.getExecutionId())
                                .eq(WorkflowNodeRun::getStatus, "WAITING")
                                .set(WorkflowNodeRun::getStatus, "CANCELLED")
                                .set(WorkflowNodeRun::getErrorCode,
                                        com.polaris.ai.workflow.contract.WorkflowErrorCode.NODE_CANCELLED.name())
                                .set(WorkflowNodeRun::getErrorMessage, "父工作流取消，子工作流同步取消")
                                .set(WorkflowNodeRun::getFinishTime, new Date()));
                persistence.appendEvent(child.getExecutionId(), null, null,
                        "EXECUTION_CANCELLED", null, null,
                        Map.of("reason", "PARENT_CANCELLED"));
            } else {
                executionMapper.requestCancel(child.getExecutionId());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionView retry(
            String executionId, WorkflowExecutionRetryCommand command) {
        requireStartEnabled();
        WorkflowExecution source = requireExecution(executionId);
        if (!Set.of("FAILED", "CANCELLED", "REJECTED").contains(source.getStatus())) {
            throw new ServiceException("只有失败、取消或拒绝的执行可以安全重试");
        }
        WorkflowVersion version = versionMapper.selectByVersionId(source.getWorkflowVersionId());
        if (version == null || !"PUBLISHED".equals(version.getStatus())) {
            throw new ServiceException("原执行绑定的发布版本不存在或已退役");
        }
        WorkflowExecutionPlan plan = readPlan(version.getExecutionPlanJson());
        if (plan.getNodes().stream().anyMatch(node -> "WRITE".equals(node.getSideEffect()))) {
            throw new ServiceException("包含写副作用节点的执行不能整体自动重试，请先人工确认外部状态");
        }
        WorkflowDefinition definition = definitionMapper.selectById(source.getDefinitionId());
        if (definition == null || !"0".equals(definition.getDelFlag())) {
            throw new ServiceException("原执行所属工作流不存在或无权访问");
        }
        JsonNode input;
        try {
            input = objectMapper.readTree(source.getInputJson());
        } catch (Exception e) {
            throw new ServiceException("原执行输入格式无效，无法安全重试");
        }
        String idempotencyKey = command == null ? null : command.idempotencyKey();
        return startDefinition(definition, source.getWorkflowVersionId(), input,
                source.getEnvironment(), idempotencyKey);
    }

    private WorkflowExecution requireExecution(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            throw new ServiceException("工作流执行ID不能为空");
        }
        WorkflowExecution execution = executionMapper.selectByExecutionId(executionId);
        if (execution == null || !Objects.equals(execution.getTenantId(), currentTenantId())) {
            throw new ServiceException("工作流执行不存在或无权访问");
        }
        return execution;
    }

    private void applyTenantScope(LambdaQueryWrapper<WorkflowExecution> query, Long tenantId) {
        if (tenantId == null) {
            query.isNull(WorkflowExecution::getTenantId);
        } else {
            query.eq(WorkflowExecution::getTenantId, tenantId);
        }
    }

    private void assertNoRecursiveWorkflow(
            WorkflowExecution parent, String childWorkflowCode) {
        WorkflowExecution current = parent;
        for (int depth = 0; current != null && depth <= 5; depth++) {
            if (childWorkflowCode.equals(current.getWorkflowCode())) {
                throw new ServiceException("子工作流调用链不能递归引用同一工作流");
            }
            String parentExecutionId = current.getParentExecutionId();
            current = parentExecutionId == null
                    ? null : executionMapper.selectByExecutionId(parentExecutionId);
        }
    }

    private WorkflowExecutionPlan readPlan(String json) {
        try {
            return objectMapper.readValue(json, WorkflowExecutionPlan.class);
        } catch (Exception e) {
            throw new ServiceException("执行计划格式无效");
        }
    }

    private void ensureSameRequest(
            WorkflowExecution existing,
            Long definitionId,
            String versionId,
            String inputJson) {
        if (!definitionId.equals(existing.getDefinitionId())
                || !versionId.equals(existing.getWorkflowVersionId())
                || !inputJson.equals(existing.getInputJson())) {
            throw new ServiceException("幂等键已被不同的工作流执行请求使用");
        }
    }

    private Principal currentPrincipal(WorkflowDefinition definition) {
        CallerContext caller = CallerUtils.getContext();
        Long tenantId = currentTenantId();
        if (!Objects.equals(definition.getTenantId(), tenantId)) {
            throw new ServiceException("工作流不属于当前操作范围");
        }
        String username = CallerUtils.getUsername();
        String type;
        String id;
        if (username != null && username.startsWith("apikey:")) {
            type = "API_KEY";
            id = username.substring("apikey:".length());
        } else if (caller.isPlatformMode()) {
            type = "PLATFORM_USER";
            id = String.valueOf(caller.getUserId());
        } else {
            type = "ADMIN";
            id = String.valueOf(caller.getUserId());
        }
        String scope = (tenantId == null ? "system" : "tenant:" + tenantId)
                + ":" + type + ":" + id;
        return new Principal(tenantId, type, id, scope);
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

    private String normalizeEnvironment(String value) {
        String environment = value == null || value.isBlank()
                ? "PROD" : value.trim().toUpperCase(Locale.ROOT);
        if (!ENVIRONMENTS.contains(environment)) {
            throw new ServiceException("运行环境只能是DEV、TEST或PROD");
        }
        return environment;
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > 128 || !result.matches("[A-Za-z0-9_.:-]+")) {
            throw new ServiceException("幂等键格式无效");
        }
        return result;
    }

    private boolean isTerminal(String status) {
        return Set.of("SUCCEEDED", "FAILED", "CANCELLED", "REJECTED").contains(status);
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ServiceException("工作流 尚未启用");
        }
        Long tenantId = currentTenantId();
        if (CallerUtils.isPlatformMode() && !properties.isEnabledForTenant(tenantId)) {
            throw new ServiceException("当前租户尚未启用 工作流");
        }
    }

    private void requireStartEnabled() {
        requireEnabled();
        if (!properties.isAcceptingNewExecutions()) {
            throw new ServiceException("工作流 已暂停创建新执行，存量执行仍会继续恢复");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ServiceException("工作流数据序列化失败");
        }
    }

    private WorkflowExecutionView view(WorkflowExecution execution) {
        return new WorkflowExecutionView(
                execution.getExecutionId(),
                execution.getParentExecutionId(),
                execution.getRootExecutionId(),
                execution.getExecutionDepth(),
                execution.getDefinitionId(),
                execution.getWorkflowVersionId(),
                execution.getWorkflowCode(),
                execution.getVersionNo(),
                execution.getStatus(),
                dataRedactor.redactJson(execution.getInputJson()),
                dataRedactor.redactJson(execution.getOutputJson()),
                execution.getBudgetJson(),
                execution.getUsageJson(),
                execution.getErrorCode(),
                execution.getErrorMessage(),
                execution.getEventSequence(),
                execution.getCancelRequested(),
                execution.getCreateTime(),
                execution.getStartTime(),
                execution.getFinishTime());
    }

    private WorkflowNodeRunView nodeRunView(WorkflowNodeRun run) {
        return new WorkflowNodeRunView(
                run.getNodeRunId(), run.getNodeId(), run.getBranchPath(), run.getAttemptNo(),
                run.getHandlerVersion(), run.getStatus(), run.getSideEffect(),
                run.getSideEffectStatus(), dataRedactor.redactJson(run.getOutputJson()), run.getErrorCode(),
                run.getErrorMessage(), run.getStartTime(), run.getFinishTime());
    }

    private WorkflowExecutionEventView eventView(WorkflowEvent event) {
        return new WorkflowExecutionEventView(
                event.getSequenceNo(), event.getEventType(), event.getNodeRunId(),
                event.getNodeId(), dataRedactor.redactJson(event.getPayloadJson()), event.getCreateTime());
    }

    private record Principal(Long tenantId, String type, String id, String scope) {
    }
}
