package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowArtifact;
import org.apache.ibatis.annotations.Mapper;

/** 工作流私有产物元数据访问接口。 */
@Mapper
public interface WorkflowArtifactMapper extends BaseMapper<WorkflowArtifact> {
}
