package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.application.WorkflowApprovalScheduleChanged;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.mapper.WorkflowApprovalInstanceMapper;
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

/** 使用单个 RAM Quartz 闹钟调度数据库中最近的审批提醒或截止时间。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.workflow", name = "enabled", havingValue = "true")
@ConditionalOnProperty(
        prefix = "ai.workflow", name = "approval-scheduler-mode",
        havingValue = "quartz", matchIfMissing = true)
public class WorkflowQuartzApprovalCoordinator {

    static final String GROUP = "WORKFLOW_APPROVAL";
    static final JobKey JOB_KEY = JobKey.jobKey("workflow-approval-due", GROUP);
    static final TriggerKey TRIGGER_KEY =
            TriggerKey.triggerKey("workflow-approval-due", GROUP);
    static final String SCHEDULED_TIME = "workflowApprovalScheduledTime";

    private final Scheduler scheduler;
    private final WorkflowApprovalInstanceMapper approvalMapper;
    private final WorkflowProperties properties;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final Object scheduleLock = new Object();

    public WorkflowQuartzApprovalCoordinator(
            Scheduler scheduler,
            WorkflowApprovalInstanceMapper approvalMapper,
            WorkflowProperties properties,
            @Qualifier("workflowTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.scheduler = scheduler;
        this.approvalMapper = approvalMapper;
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
                for (JobKey key : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(GROUP))) {
                    scheduler.deleteJob(key);
                }
            }
            syncSafely();
            log.info("Workflow approval Quartz scheduler initialized");
        } catch (Exception e) {
            log.error("Unable to rebuild workflow approval Quartz schedule", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScheduleChanged(WorkflowApprovalScheduleChanged event) {
        submitSync();
    }

    /** RAMJobStore 丢失或瞬时调度失败时的低频安全核对。 */
    @Scheduled(
            fixedDelayString = "${ai.workflow.approval-quartz-reconcile-ms:600000}",
            initialDelayString = "${ai.workflow.approval-quartz-reconcile-ms:600000}")
    public void reconcile() {
        syncSafely();
    }

    private void submitSync() {
        try {
            taskExecutor.execute(this::syncSafely);
        } catch (RuntimeException e) {
            log.warn("Workflow approval Quartz sync rejected", e);
        }
    }

    private void syncSafely() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            sync();
        } catch (RuntimeException e) {
            log.warn("Unable to sync workflow approval Quartz schedule", e);
        }
    }

    private void sync() {
        try {
            synchronized (scheduleLock) {
                Date due = approvalMapper.selectNextScheduledActionTime();
                if (due == null) {
                    if (scheduler.checkExists(JOB_KEY)) {
                        scheduler.deleteJob(JOB_KEY);
                    }
                    return;
                }

                JobDetail existingJob = scheduler.getJobDetail(JOB_KEY);
                Trigger existingTrigger = scheduler.getTrigger(TRIGGER_KEY);
                if (existingJob != null && existingTrigger != null
                        && existingJob.getJobDataMap().getLong(SCHEDULED_TIME)
                        == due.getTime()) {
                    return;
                }
                if (scheduler.checkExists(JOB_KEY)) {
                    scheduler.deleteJob(JOB_KEY);
                }

                JobDetail job = JobBuilder.newJob(WorkflowQuartzApprovalJob.class)
                        .withIdentity(JOB_KEY)
                        .usingJobData(SCHEDULED_TIME, due.getTime())
                        .build();
                Trigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity(TRIGGER_KEY)
                        .forJob(job)
                        .startAt(due)
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withRepeatCount(0)
                                .withMisfireHandlingInstructionFireNow())
                        .build();
                scheduler.scheduleJob(job, trigger);
            }
        } catch (Exception e) {
            throw new IllegalStateException("同步工作流审批 Quartz 闹钟失败", e);
        }
    }
}
