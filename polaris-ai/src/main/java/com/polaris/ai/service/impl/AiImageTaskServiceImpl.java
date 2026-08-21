package com.polaris.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiImageTask;
import com.polaris.ai.mapper.AiImageTaskMapper;
import com.polaris.ai.service.IAiImageTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * AI 图像生成任务服务层实现
 *
 * @author polaris
 */
@Slf4j
@Service
public class AiImageTaskServiceImpl extends ServiceImpl<AiImageTaskMapper, AiImageTask>
        implements IAiImageTaskService {

    /** error_msg 为 TEXT，仍设一个上限避免超长堆栈入库 */
    private static final int MAX_ERR_LEN = 2000;

    @Override
    public AiImageTask createTask(AiImageTask task) {
        if (task.getStatus() == null) {
            task.setStatus("0");
        }
        if (task.getCreateTime() == null) {
            task.setCreateTime(new Date());
        }
        // 审计字段（工具在用户请求线程内创建任务，安全上下文可用；异常兜底忽略）
        try {
            CallerContext ctx = CallerContextHolder.get();
            if (task.getCreateBy() == null) {
                task.setCreateBy(ctx != null ? ctx.getUsername() : "system");
            }
            if (task.getDeptId() == null && ctx != null && ctx.getDeptId() != null) {
                task.setDeptId(ctx.getDeptId());
            }
        } catch (Exception ignored) {
            // 无登录上下文（如系统内部调用）时跳过
        }
        this.save(task);
        return task;
    }

    @Override
    public void markSuccess(String taskId, String imageUrl, Long costTime) {
        LambdaUpdateWrapper<AiImageTask> uw = new LambdaUpdateWrapper<>();
        uw.eq(AiImageTask::getTaskId, taskId)
          .set(AiImageTask::getStatus, "1")
          .set(AiImageTask::getImageUrl, imageUrl)
          .set(AiImageTask::getCostTime, costTime)
          .set(AiImageTask::getUpdateTime, new Date());
        this.update(uw);
    }

    @Override
    public void markFail(String taskId, String errorMsg) {
        String msg = errorMsg;
        if (msg != null && msg.length() > MAX_ERR_LEN) {
            msg = msg.substring(0, MAX_ERR_LEN);
        }
        LambdaUpdateWrapper<AiImageTask> uw = new LambdaUpdateWrapper<>();
        uw.eq(AiImageTask::getTaskId, taskId)
          .set(AiImageTask::getStatus, "2")
          .set(AiImageTask::getErrorMsg, msg)
          .set(AiImageTask::getUpdateTime, new Date());
        this.update(uw);
    }

    @Override
    public AiImageTask getTask(String taskId) {
        return this.getById(taskId);
    }

    @Override
    public int recoverInterruptedTasks() {
        LambdaQueryWrapper<AiImageTask> qw = new LambdaQueryWrapper<>();
        qw.eq(AiImageTask::getStatus, "0");
        long stuck = this.count(qw);
        if (stuck == 0) {
            return 0;
        }
        LambdaUpdateWrapper<AiImageTask> uw = new LambdaUpdateWrapper<>();
        uw.eq(AiImageTask::getStatus, "0")
          .set(AiImageTask::getStatus, "2")
          .set(AiImageTask::getErrorMsg, "服务重启导致任务中断，请重新发起绘图")
          .set(AiImageTask::getUpdateTime, new Date());
        this.update(uw);
        log.warn(">>> [AiImageTaskService] 重启恢复：已将 {} 个中断的绘图任务标记为失败", stuck);
        return (int) stuck;
    }

    /** 应用启动完成后执行一次重启兜底 */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            recoverInterruptedTasks();
        } catch (Exception e) {
            log.error(">>> [AiImageTaskService] 重启恢复执行异常", e);
        }
    }
}
