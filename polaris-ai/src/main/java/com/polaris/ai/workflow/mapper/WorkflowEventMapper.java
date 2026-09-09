package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowEvent;
import org.apache.ibatis.annotations.Mapper;

/** 工作流持久化事件数据访问接口。 */
@Mapper
public interface WorkflowEventMapper extends BaseMapper<WorkflowEvent> {
}
