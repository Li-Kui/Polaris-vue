package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowCheckpoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 工作流恢复检查点数据访问接口。 */
@Mapper
public interface WorkflowCheckpointMapper extends BaseMapper<WorkflowCheckpoint> {

    @Select("SELECT * FROM ai_workflow_checkpoint WHERE execution_id = #{executionId} "
            + "AND status = 'SAFE' ORDER BY sequence_no DESC LIMIT 1")
    WorkflowCheckpoint selectLatestSafe(@Param("executionId") String executionId);
}
