package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowApprovalTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 工作流持久化审批任务的数据访问接口。 */
@Mapper
public interface WorkflowApprovalTaskMapper extends BaseMapper<WorkflowApprovalTask> {

    @Select("SELECT * FROM ai_workflow_approval_task "
            + "WHERE approval_task_id = #{approvalTaskId} LIMIT 1")
    WorkflowApprovalTask selectByTaskId(@Param("approvalTaskId") String approvalTaskId);

    @Select("SELECT * FROM ai_workflow_approval_task "
            + "WHERE approval_task_id = #{approvalTaskId} FOR UPDATE")
    WorkflowApprovalTask selectByTaskIdForUpdate(
            @Param("approvalTaskId") String approvalTaskId);

    @Select("SELECT * FROM ai_workflow_approval_task "
            + "WHERE execution_id = #{executionId} AND node_run_id = #{nodeRunId} LIMIT 1")
    WorkflowApprovalTask selectByNodeRun(
            @Param("executionId") String executionId,
            @Param("nodeRunId") String nodeRunId);
}
