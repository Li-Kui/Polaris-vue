package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.service.WorkflowApprovalService;
import com.polaris.common.utils.spring.SpringUtils;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/** Quartz 到点后处理审批提醒/过期，并重建下一个数据库事实时间。 */
@DisallowConcurrentExecution
public class WorkflowQuartzApprovalJob implements Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            SpringUtils.getBean(WorkflowApprovalService.class).expirePendingTasks();
        } catch (RuntimeException e) {
            throw new JobExecutionException("工作流审批定时动作执行失败", e, false);
        }
    }
}
