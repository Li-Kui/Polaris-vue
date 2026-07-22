package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.domain.AiGraphCheckpoint;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图工作流检查点 Mapper（二期启用）
 *
 * @author polaris
 */
@Mapper
public interface AiGraphCheckpointMapper extends BaseMapper<AiGraphCheckpoint> {
}
