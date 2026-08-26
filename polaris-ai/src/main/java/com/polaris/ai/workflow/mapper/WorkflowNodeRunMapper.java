package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowNodeRun;
import org.apache.ibatis.annotations.Mapper;

/** 工作流节点尝试记录数据访问接口。 */
@Mapper
public interface WorkflowNodeRunMapper extends BaseMapper<WorkflowNodeRun> {
}
