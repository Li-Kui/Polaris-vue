package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.application.WorkflowTriggerFirePending;
import com.polaris.ai.workflow.application.WorkflowTriggerScheduleChanged;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.domain.WorkflowTrigger;
import com.polaris.ai.workflow.mapper.WorkflowTriggerFireMapper;
import com.polaris.ai.workflow.mapper.WorkflowTriggerMapper;
import com.polaris.common.exception.ServiceException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** 原子地落触发批次并推进业务表中的下一触发时间。 */
@Service
public class WorkflowTriggerFireMaterializer {

    private static final int DEFAULT_MISFIRE_GRACE_SECONDS = 5;
    private static final int DEFAULT_MAX_CATCH_UP = 10;

    private final WorkflowTriggerMapper triggerMapper;
    private final WorkflowTriggerFireMapper fireMapper;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public WorkflowTriggerFireMaterializer(
            WorkflowTriggerMapper triggerMapper,
            WorkflowTriggerFireMapper fireMapper,
            WorkflowProperties properties,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        this.triggerMapper = triggerMapper;
        this.fireMapper = fireMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public void materialize(String triggerId, Date expectedScheduledTime) {
        if (!properties.isEnabled() || !properties.isQuartzTriggerScheduler()
                || triggerId == null || expectedScheduledTime == null) {
            return;
        }
        WorkflowTrigger trigger = triggerMapper.selectByTriggerIdForUpdate(triggerId);
        if (trigger == null || !"ACTIVE".equals(trigger.getStatus())
                || !"SCHEDULE".equals(trigger.getTriggerType())
                || trigger.getNextFireTime() == null
                || trigger.getNextFireTime().getTime() != expectedScheduledTime.getTime()) {
            return;
        }

        JsonNode config = readConfig(trigger);
        Date now = new Date();
        int graceSeconds = clamp(config.path("misfireGraceSeconds")
                .asInt(DEFAULT_MISFIRE_GRACE_SECONDS), 0, 3600);
        boolean misfired = expectedScheduledTime.toInstant()
                .plusSeconds(graceSeconds).isBefore(now.toInstant());
        MisfirePolicy policy = policy(config);
        Date next;
        Date lastFireTime = expectedScheduledTime;
        boolean firePlanned = true;

        if (!misfired) {
            insertPending(trigger, expectedScheduledTime);
            next = nextFireTime(config, expectedScheduledTime);
        } else if (policy == MisfirePolicy.SKIP) {
            firePlanned = false;
            next = nextFireTime(config, now);
        } else if (policy == MisfirePolicy.CATCH_UP) {
            int maxCatchUp = clamp(config.path("maxCatchUpCount")
                    .asInt(DEFAULT_MAX_CATCH_UP), 1, 100);
            Date cursor = expectedScheduledTime;
            int count = 0;
            while (cursor != null && !cursor.after(now) && count < maxCatchUp) {
                insertPending(trigger, cursor);
                lastFireTime = cursor;
                cursor = nextFireTime(config, cursor);
                count++;
            }
            next = cursor != null && cursor.after(now) ? cursor : nextFireTime(config, now);
        } else {
            insertPending(trigger, expectedScheduledTime);
            next = nextFireTime(config, now);
        }

        if (triggerMapper.advanceScheduledFire(
                triggerId, expectedScheduledTime, lastFireTime, next) != 1) {
            throw new ServiceException("工作流定时触发器已被并发修改");
        }
        eventPublisher.publishEvent(new WorkflowTriggerScheduleChanged(triggerId));
        if (firePlanned) {
            eventPublisher.publishEvent(new WorkflowTriggerFirePending(triggerId));
        }
    }

    private void insertPending(WorkflowTrigger trigger, Date scheduledTime) {
        String source = trigger.getTriggerId() + "\u0000" + scheduledTime.getTime();
        String fireId = UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
        fireMapper.insertPending(
                trigger.getTenantId(), fireId, trigger.getTriggerId(), scheduledTime);
    }

    private JsonNode readConfig(WorkflowTrigger trigger) {
        try {
            return objectMapper.readTree(trigger.getConfigJson());
        } catch (Exception e) {
            throw new ServiceException("工作流定时触发器配置已损坏");
        }
    }

    private Date nextFireTime(JsonNode config, Date after) {
        try {
            ZoneId zone = ZoneId.of(config.path("timezone").asString("Asia/Shanghai"));
            ZonedDateTime next = CronExpression.parse(config.path("cron").asString())
                    .next(ZonedDateTime.ofInstant(after.toInstant(), zone));
            if (next == null) {
                throw new ServiceException("工作流定时触发器没有可计算的下次运行时间");
            }
            return Date.from(next.toInstant());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("无法计算工作流定时触发器下次运行时间");
        }
    }

    private MisfirePolicy policy(JsonNode config) {
        try {
            return MisfirePolicy.valueOf(config.path("misfirePolicy")
                    .asString("FIRE_ONCE").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MisfirePolicy.FIRE_ONCE;
        }
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private enum MisfirePolicy {
        FIRE_ONCE,
        SKIP,
        CATCH_UP
    }
}
