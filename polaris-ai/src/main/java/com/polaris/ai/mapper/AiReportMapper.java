package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.domain.AiReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 分析报告 数据层 Mapper
 *
 * @author polaris
 */
@Mapper
public interface AiReportMapper extends BaseMapper<AiReport> {
}
