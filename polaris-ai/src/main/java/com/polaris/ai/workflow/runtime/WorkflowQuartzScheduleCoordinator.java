package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.application.WorkflowTriggerScheduleChanged;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.domain.WorkflowTrigger;
import com.polaris.ai.workflow.mapper.WorkflowTriggerMapper;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/** 从业务表重建并维护 RAM Quartz 中的单次闹钟。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.workflow", name = "enabled", havingValue = "true")
@ConditionalOnProperty(
        prefix = "ai.workflow", name = "trigger-scheduler-mode",
        havingValue = "quartz", matchIfMissing = true)
public class WorkflowQuartzScheduleCoordinator {

    private final Scheduler scheduler;
    private final WorkflowTriggerMapper triggerMapper;
    private final WorkflowProperties properties;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final Object scheduleLock = new Object();

    public WorkflowQuartzScheduleCoordinator(
            Scheduler scheduler,
            WorkflowTriggerMapper triggerMapper,
            WorkflowProperties properties,
            @Qualifier("workflowTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.scheduler = scheduler;
        this.triggerMapper = triggerMapper;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildAtStartup() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            synchronized (scheduleLock) {
                for (JobKey key : scheduler.getJobKeys(
                        GroupMatcher.jobGroupEquals(WorkflowQuartzKeys.GROUP))) {
                    scheduler.deleteJob(key);
                }
            }
            var activeSchedules = triggerMapper.selectActiveSchedules();
            for (WorkflowTrigger trigger : activeSchedules) {
                syncSafely(trigger.getTriggerId());
            }
            log.info("Workflow Quartz scheduler initialized, activeSchedules={}",
                    activeSchedules.size());
        } catch (Exception e) {
            log.error("Unable to rebuild workflow Quartz schedules", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScheduleChanged(WorkflowTriggerScheduleChanged event) {
        submitSync(event.triggerId());
    }

    /** RAMJobStore 丢失或瞬时调度失败时的低频安全网，不承担常态触发。 */
    @Scheduled(
            fixedDelayString = "${ai.workflow.trigger-quartz-reconcile-ms:600000}",
            initialDelayString = "${ai.workflow.trigger-quartz-reconcile-initial-delay-ms:600000}")
    public void reconcile() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            var activeSchedules = triggerMapper.selectActiveSchedules();
            Set<JobKey> activeKeys = new HashSet<>();
            for (WorkflowTrigger trigger : activeSchedules) {
                activeKeys.add(WorkflowQuartzKeys.jobKey(trigger.getTriggerId()));
                syncSafely(trigger.getTriggerId());
            }
            synchronized (scheduleLock) {
                for (JobKey key : scheduler.getJobKeys(
                        GroupMatcher.jobGroupEquals(WorkflowQuartzKeys.GROUP))) {
                    if (!activeKeys.contains(key)) {
                        scheduler.deleteJob(key);
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("Unable to reconcile workflow Quartz schedules", e);
        } catch (Exception e) {
            log.warn("Unable to remove orphaned workflow Quartz schedules", e);
        }
    }

    private void submitSync(String triggerId) {
        try {
            taskExecutor.execute(() -> syncSafely(triggerId));
        } catch (RuntimeException e) {
            log.warn("Workflow Quartz schedule sync rejected, triggerId={}", triggerId, e);
        }
    }

    private void syncSafely(String triggerId) {
        try {
            sync(triggerId);
        } catch (RuntimeException e) {
            log.warn("Unable to sync workflow Quartz schedule, triggerId={}", triggerId, e);
        }
    }

    private void sync(String triggerId) {
        JobKey jobKey = WorkflowQuartzKeys.jobKey(triggerId);
        TriggerKey triggerKey = WorkflowQuartzKeys.triggerKey(triggerId);
        try {
            synchronized (scheduleLock) {
                WorkflowTrigger workflowTrigger =
                        triggerMapper.selectByTriggerIdInternal(triggerId);
                if (workflowTrigger == null || !"ACTIVE".equals(workflowTrigger.getStatus())
                        || !"SCHEDULE".equals(workflowTrigger.getTriggerType())
                        || workflowTrigger.getNextFireTime() == null) {
                    if (scheduler.checkExists(jobKey)) {
                        scheduler.deleteJob(jobKey);
                    }
                    return;
                }

                Date due = workflowTrigger.getNextFireTime();
                JobDetail existingJob = scheduler.getJobDetail(jobKey);
                Trigger existingTrigger = scheduler.getTrigger(triggerKey);
                if (existingJob != null && existingTrigger != null
                        && existingJob.getJobDataMap().getLong(
                                WorkflowQuartzTriggerJob.SCHEDULED_TIME) == due.getTime()) {
                    return;
                }
                if (scheduler.checkExists(jobKey)) {
                    scheduler.deleteJob(jobKey);
                }

                JobDetail job = JobBuilder.newJob(WorkflowQuartzTriggerJob.class)
                        .withIdentity(jobKey)
                        .usingJobData(WorkflowQuartzTriggerJob.TRIGGER_ID, triggerId)
                        .usingJobData(WorkflowQuartzTriggerJob.SCHEDULED_TIME, due.getTime())
                        .build();
                Trigger quartzTrigger = TriggerBuilder.newTrigger()
                        .withIdentity(triggerKey)
                        .forJob(job)
                        .startAt(due)
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withRepeatCount(0)
                                .withMisfireHandlingInstructionFireNow())
                        .build();
                scheduler.scheduleJob(job, quartzTrigger);
            }
        } catch (Exception e) {
            throw new IllegalStateException("同步工作流 Quartz 闹钟失败", e);
        }
    }
}
