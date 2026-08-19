package com.polaris.ai.safety.service;

import com.polaris.ai.safety.vo.ModerationSummaryView;

/**
 * AI 敏感内容安全统计与告警服务层接口
 *
 * @author polaris
 */
public interface IModerationStatisticsService {

    /**
     * 获取指定时间窗口（默认7天）的安全检测统计汇总与趋势
     */
    ModerationSummaryView getSummary(Integer days);
}
