package com.polaris.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.polaris.ai.domain.AiImageTask;

/**
 * AI 图像生成任务服务层接口
 *
 * @author polaris
 */
public interface IAiImageTaskService extends IService<AiImageTask> {

    /** 创建并落库任务（自动填充审计字段，status=0 生成中） */
    AiImageTask createTask(AiImageTask task);

    /** 标记任务成功 */
    void markSuccess(String taskId, String imageUrl, Long costTime);

    /** 标记任务失败 */
    void markFail(String taskId, String errorMsg);

    /** 查询单个任务 */
    AiImageTask getTask(String taskId);

    /**
     * 重启恢复兜底：将服务重启前遗留在"生成中(0)"的任务统一置为失败，
     * 避免前端无限轮询僵尸任务。返回修复条数。
     */
    int recoverInterruptedTasks();
}
