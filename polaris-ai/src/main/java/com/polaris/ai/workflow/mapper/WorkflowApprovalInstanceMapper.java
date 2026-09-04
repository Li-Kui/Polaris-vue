package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowApprovalInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/** 审批实例数据访问接口。 */
@Mapper
public interface WorkflowApprovalInstanceMapper extends BaseMapper<WorkflowApprovalInstance> {

    @Select("SELECT * FROM ai_workflow_approval_instance "
            + "WHERE approval_instance_id = #{instanceId} LIMIT 1")
    WorkflowApprovalInstance selectByInstanceId(@Param("instanceId") String instanceId);

    @Select("SELECT * FROM ai_workflow_approval_instance "
            + "WHERE approval_instance_id = #{instanceId} FOR UPDATE")
    WorkflowApprovalInstance selectByInstanceIdForUpdate(@Param("instanceId") String instanceId);

    @Select("SELECT * FROM ai_workflow_approval_instance "
            + "WHERE execution_id = #{executionId} AND node_run_id = #{nodeRunId} LIMIT 1")
    WorkflowApprovalInstance selectByNodeRun(
            @Param("executionId") String executionId,
            @Param("nodeRunId") String nodeRunId);

    @Select("SELECT i.* FROM ai_workflow_approval_instance i "
            + "JOIN ai_workflow_approval_stage s "
            + "ON s.stage_instance_id = i.current_stage_id "
            + "WHERE i.status = 'PENDING' AND s.status = 'ACTIVE' "
            + "AND ((i.deadline IS NOT NULL AND i.deadline <= #{now}) "
            + "OR (s.deadline IS NOT NULL AND s.deadline <= #{now})) "
            + "ORDER BY CASE WHEN i.deadline IS NULL THEN s.deadline "
            + "WHEN s.deadline IS NULL THEN i.deadline "
            + "WHEN i.deadline < s.deadline THEN i.deadline ELSE s.deadline END ASC "
            + "LIMIT #{limit}")
    List<WorkflowApprovalInstance> selectExpiredCandidates(
            @Param("now") Date now,
            @Param("limit") int limit);

    @Select("SELECT * FROM ai_workflow_approval_instance "
            + "WHERE status = 'PENDING' AND reminder_time IS NOT NULL "
            + "AND reminder_sent_time IS NULL AND reminder_time <= #{now} "
            + "ORDER BY reminder_time ASC LIMIT #{limit}")
    List<WorkflowApprovalInstance> selectReminderCandidates(
            @Param("now") Date now,
            @Param("limit") int limit);
}
