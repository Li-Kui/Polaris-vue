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
     * 调用 AI 智能体将任意 Markdown 报告重塑为 Universal Schema 结构化 JSON
     */
    Object refineReport(String reportContent);

    /**
     * 调用 AI 智能体将报告重塑为结构化 JSON（SSE 流式推送）
     */
    SseEmitter refineReportStream(String reportContent);
}
