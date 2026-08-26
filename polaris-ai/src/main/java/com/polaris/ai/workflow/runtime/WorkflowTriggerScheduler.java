package com.polaris.ai.workflow.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.core.context.SystemCallerContext;
import com.polaris.ai.core.context.TenantSystemCallerContext;
import com.polaris.ai.workflow.application.WorkflowExecutionView;
import com.polaris.ai.workflow.application.WorkflowTriggerApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowTriggerInvocationCommand;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.domain.WorkflowTrigger;
import com.polaris.ai.workflow.mapper.WorkflowTriggerMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

/** 支持多实例安全调度的发布版本持久化触发器调度器。 */
@Slf4j
@Component
public class WorkflowTriggerScheduler {

    private final WorkflowTriggerMapper triggerMapper;
    private final WorkflowTriggerApplicationFacade triggerFacade;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;

    public WorkflowTriggerScheduler(
            WorkflowTriggerMapper triggerMapper,
            WorkflowTriggerApplicationFacade triggerFacade,
            WorkflowProperties properties,
            ObjectMapper objectMapper) {
        this.triggerMapper = triggerMapper;
        this.triggerFacade = triggerFacade;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${ai.workflow.trigger-poll-ms:15000}")
    public void dispatchDueSchedules() {
        if (!properties.isEnabled() || !properties.isAcceptingNewExecutions()) return;
        for (WorkflowTrigger trigger : triggerMapper.selectDueSchedules(50)) {
            dispatch(trigger);
        }
    }

    private void dispatch(WorkflowTrigger trigger) {
        Date scheduledTime = trigger.getNextFireTime();
        try {
            JsonNode config = objectMapper.readTree(trigger.getConfigJson());
            Date next = calculateNext(config, new Date());
            if (triggerMapper.claimSchedule(trigger.getTriggerId(), next) != 1) return;
            CallerContext previous = CallerContextHolder.get();
            CallerContextHolder.set(trigger.getTenantId() == null
                    ? SystemCallerContext.INSTANCE
                    : new TenantSystemCallerContext(trigger.getTenantId()));
            try {
                String key = "schedule:" + (scheduledTime == null
                        ? System.currentTimeMillis() : scheduledTime.getTime());
                WorkflowExecutionView execution = triggerFacade.invoke(
                        trigger.getTriggerId(), new WorkflowTriggerInvocationCommand(
                                objectMapper.createObjectNode(), key));
                triggerMapper.recordTriggerResult(
                        trigger.getTriggerId(), execution.executionId(), "DISPATCHED", null);
            } finally {
                CallerContextHolder.clear();
                if (previous != null) CallerContextHolder.set(previous);
            }
        } catch (Exception e) {
            String message = e.getMessage() == null ? "触发执行失败" : e.getMessage();
            triggerMapper.recordTriggerResult(trigger.getTriggerId(), null,
                    "FAILED", message.substring(0, Math.min(message.length(), 500)));
            log.warn("工作流定时触发失败, triggerId={}", trigger.getTriggerId(), e);
        }
    }

    private Date calculateNext(JsonNode config, Date after) {
        ZoneId zone = ZoneId.of(config.path("timezone").asText("Asia/Shanghai"));
        ZonedDateTime next = CronExpression.parse(config.path("cron").asText())
                .next(ZonedDateTime.ofInstant(after.toInstant(), zone));
        if (next == null) throw new IllegalStateException("无法计算下次触发时间");
        return Date.from(next.toInstant());
    }
}
