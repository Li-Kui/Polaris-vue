package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowApprovalDecision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 审批决定数据访问接口。 */
@Mapper
public interface WorkflowApprovalDecisionMapper extends BaseMapper<WorkflowApprovalDecision> {

    @Select("SELECT * FROM ai_workflow_approval_decision "
            + "WHERE stage_instance_id = #{stageInstanceId} ORDER BY create_time ASC, id ASC")
    List<WorkflowApprovalDecision> selectByStageId(
            @Param("stageInstanceId") String stageInstanceId);

    @Select("SELECT * FROM ai_workflow_approval_decision "
            + "WHERE approval_instance_id = #{approvalInstanceId} ORDER BY create_time ASC, id ASC")
    List<WorkflowApprovalDecision> selectByInstanceId(
            @Param("approvalInstanceId") String approvalInstanceId);

    @Select("SELECT * FROM ai_workflow_approval_decision "
            + "WHERE stage_instance_id = #{stageInstanceId} AND actor_id = #{actorId} LIMIT 1")
    WorkflowApprovalDecision selectByStageAndActor(
            @Param("stageInstanceId") String stageInstanceId,
            @Param("actorId") String actorId);

    @Select("SELECT * FROM ai_workflow_approval_decision "
            + "WHERE request_id = #{requestId} LIMIT 1")
    WorkflowApprovalDecision selectByRequestId(@Param("requestId") String requestId);
}
