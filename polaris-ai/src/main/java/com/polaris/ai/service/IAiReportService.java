package com.polaris.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.polaris.ai.domain.AiReport;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 分析报告服务层接口
 *
 * @author polaris
 */
public interface IAiReportService extends IService<AiReport> {

    /**
     * 保存报告
     */
    boolean saveReport(AiReport report);

    /**
     * 条件查询报告列表
     */
    List<AiReport> selectReportList(AiReport report);

    /**
     * 获取当前用户有权访问的报告详情
     */
    AiReport getReportById(Long id);

    /**
     * 更新报告中心允许修改的字段
     */
    boolean updateReport(AiReport report);

    /**
     * 删除当前用户有权访问的报告
     */
    boolean removeReport(Long id);

    /**
     * 创建或复用报告中心的异步美化任务
     */
    java.util.Map<String, Object> startRefineReport(Long reportId);

    /**
     * 查询报告中心美化任务状态
     */
    java.util.Map<String, Object> getRefineStatus(Long reportId);

    /**
     * 在后台任务中调用 AI 并持久化报告中心的美化结果
     */
    Object refineReport(Long reportId);

    /**
     * 调用 AI 智能体将任意 Markdown 报告重塑为 Universal Schema 结构化 JSON
     */
    Object refineReport(String reportContent);

    /**
     * 调用 AI 智能体将报告重塑为结构化 JSON（SSE 流式推送）
     */
    SseEmitter refineReportStream(String reportContent);
}
