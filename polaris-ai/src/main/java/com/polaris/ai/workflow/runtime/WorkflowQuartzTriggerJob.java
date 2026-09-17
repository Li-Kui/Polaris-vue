package com.polaris.ai.workflow.runtime;

import com.polaris.common.utils.spring.SpringUtils;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.Date;

/** 单次 Quartz 闹钟：只持久化应触发批次，不在 Quartz 线程执行工作流。 */
@DisallowConcurrentExecution
public class WorkflowQuartzTriggerJob implements Job {

    static final String TRIGGER_ID = "workflowTriggerId";
    static final String SCHEDULED_TIME = "workflowScheduledTime";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String triggerId = context.getMergedJobDataMap().getString(TRIGGER_ID);
        long scheduledTime = context.getMergedJobDataMap().getLong(SCHEDULED_TIME);
        try {
            SpringUtils.getBean(WorkflowTriggerFireMaterializer.class)
                    .materialize(triggerId, new Date(scheduledTime));
        } catch (RuntimeException e) {
            throw new JobExecutionException("工作流定时触发批次持久化失败", e, false);
        }
    }
}
