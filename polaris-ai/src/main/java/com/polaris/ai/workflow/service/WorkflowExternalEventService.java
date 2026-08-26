package com.polaris.ai.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.core.context.TenantSystemCallerContext;
import com.polaris.ai.workflow.application.*;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.domain.WorkflowTrigger;
import com.polaris.ai.workflow.mapper.WorkflowTriggerMapper;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 校验外部事件并调度匹配的租户工作流触发器。 */
@Service
public class WorkflowExternalEventService implements WorkflowExternalEventApplicationFacade {

    private static final int MAX_EVENT_BYTES = 1024 * 1024;

    private final WorkflowTriggerMapper triggerMapper;
    private final WorkflowTriggerApplicationFacade triggerFacade;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;

    public WorkflowExternalEventService(
            WorkflowTriggerMapper triggerMapper,
            WorkflowTriggerApplicationFacade triggerFacade,
            WorkflowProperties properties,
            ObjectMapper objectMapper) {
        this.triggerMapper = triggerMapper;
        this.triggerFacade = triggerFacade;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<WorkflowExecutionView> dispatch(WorkflowExternalEventCommand command) {
        validate(command);
        JsonNode payload = command.payload() == null
                ? objectMapper.createObjectNode() : command.payload();
        ensurePayloadSize(payload);
        List<WorkflowTrigger> triggers = triggerMapper.selectList(
                new LambdaQueryWrapper<WorkflowTrigger>()
                        .eq(WorkflowTrigger::getTenantId, command.tenantId())
                        .eq(WorkflowTrigger::getTriggerType, "EVENT")
                        .eq(WorkflowTrigger::getStatus, "ACTIVE")
                        .orderByAsc(WorkflowTrigger::getId)
                        .last("LIMIT 1000"));
        List<WorkflowExecutionView> executions = new ArrayList<>();
        CallerContext previous = CallerContextHolder.get();
        CallerContextHolder.set(new TenantSystemCallerContext(command.tenantId()));
        try {
            for (WorkflowTrigger trigger : triggers) {
                if (!matches(trigger, command.eventType())) {
                    continue;
                }
                try {
                    WorkflowExecutionView execution = triggerFacade.invoke(
                            trigger.getTriggerId(),
                            new WorkflowTriggerInvocationCommand(
                                    payload, stableEventKey(command)));
                    executions.add(execution);
                    triggerMapper.recordTriggerResult(
                            trigger.getTriggerId(), execution.executionId(),
                            "DISPATCHED", null);
                } catch (Exception e) {
                    String message = e.getMessage() == null ? "外部事件触发失败" : e.getMessage();
                    triggerMapper.recordTriggerResult(
                            trigger.getTriggerId(), null, "FAILED",
                            message.substring(0, Math.min(message.length(), 500)));
                    if (e instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new ServiceException("外部事件触发失败");
                }
            }
            return List.copyOf(executions);
        } finally {
            CallerContextHolder.clear();
            if (previous != null) {
                CallerContextHolder.set(previous);
            }
        }
    }

    private void validate(WorkflowExternalEventCommand command) {
        if (!properties.isEnabled() || !properties.isAcceptingNewExecutions()) {
            throw new ServiceException("工作流当前不接受新的外部事件执行");
        }
        if (command == null || command.tenantId() == null || command.tenantId() <= 0) {
            throw new ServiceException("外部事件租户ID无效");
        }
        if (!properties.isEnabledForTenant(command.tenantId())) {
            throw new ServiceException("目标租户尚未启用工作流");
        }
        if (command.eventType() == null
                || !command.eventType().matches("[A-Za-z][A-Za-z0-9_.-]{0,127}")) {
            throw new ServiceException("外部事件类型格式无效");
        }
        if (command.eventId() == null
                || !command.eventId().matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) {
            throw new ServiceException("外部事件ID格式无效");
        }
    }

    private void ensurePayloadSize(JsonNode payload) {
        try {
            if (objectMapper.writeValueAsBytes(payload).length > MAX_EVENT_BYTES) {
                throw new ServiceException("外部事件载荷不能超过1MB");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("外部事件载荷序列化失败");
        }
    }

    private boolean matches(WorkflowTrigger trigger, String eventType) {
        try {
            return eventType.equals(objectMapper.readTree(trigger.getConfigJson())
                    .path("eventType").asText());
        } catch (Exception e) {
            return false;
        }
    }

    private String stableEventKey(WorkflowExternalEventCommand command) {
        String source = command.tenantId() + "\u0000" + command.eventType()
                + "\u0000" + command.eventId();
        return "event:" + UUID.nameUUIDFromBytes(
                source.getBytes(StandardCharsets.UTF_8));
    }
}
