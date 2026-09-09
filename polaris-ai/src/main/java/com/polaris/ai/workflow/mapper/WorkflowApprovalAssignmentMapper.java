package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowApprovalAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 审批资格数据访问接口。 */
@Mapper
public interface WorkflowApprovalAssignmentMapper
        extends BaseMapper<WorkflowApprovalAssignment> {

    @Select("SELECT * FROM ai_workflow_approval_assignment "
            + "WHERE stage_instance_id = #{stageInstanceId} ORDER BY id ASC")
    List<WorkflowApprovalAssignment> selectByStageId(
            @Param("stageInstanceId") String stageInstanceId);

    @Select("SELECT * FROM ai_workflow_approval_assignment "
            + "WHERE stage_instance_id = #{stageInstanceId} AND user_id = #{userId} FOR UPDATE")
    WorkflowApprovalAssignment selectByStageAndUserForUpdate(
            @Param("stageInstanceId") String stageInstanceId,
            @Param("userId") String userId);
}
