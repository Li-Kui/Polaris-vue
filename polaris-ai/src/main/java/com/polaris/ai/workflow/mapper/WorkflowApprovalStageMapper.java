package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowApprovalStage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 审批级别数据访问接口。 */
@Mapper
public interface WorkflowApprovalStageMapper extends BaseMapper<WorkflowApprovalStage> {

    @Select("SELECT * FROM ai_workflow_approval_stage "
            + "WHERE stage_instance_id = #{stageInstanceId} FOR UPDATE")
    WorkflowApprovalStage selectByStageIdForUpdate(
            @Param("stageInstanceId") String stageInstanceId);

    @Select("SELECT * FROM ai_workflow_approval_stage "
            + "WHERE approval_instance_id = #{instanceId} ORDER BY sequence_no ASC")
    List<WorkflowApprovalStage> selectByInstanceId(@Param("instanceId") String instanceId);
}
