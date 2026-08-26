package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowTrigger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/** 绑定不可变版本的工作流触发器数据访问接口。 */
@Mapper
public interface WorkflowTriggerMapper extends BaseMapper<WorkflowTrigger> {

    @Update("UPDATE ai_workflow_trigger SET status = #{status}, "
            + "next_fire_time = COALESCE(#{nextFireTime}, next_fire_time), "
            + "lock_version = lock_version + 1, update_by = #{updateBy}, update_time = NOW() "
            + "WHERE trigger_id = #{triggerId} "
            + "AND ((#{tenantId} IS NULL AND tenant_id IS NULL) OR tenant_id = #{tenantId}) "
            + "AND lock_version = #{expectedLockVersion}")
    int updateStatus(
            @Param("triggerId") String triggerId,
            @Param("tenantId") Long tenantId,
            @Param("status") String status,
            @Param("expectedLockVersion") Integer expectedLockVersion,
            @Param("nextFireTime") Date nextFireTime,
            @Param("updateBy") String updateBy);

    @Select("SELECT * FROM ai_workflow_trigger WHERE status = 'ACTIVE' "
            + "AND trigger_type = 'SCHEDULE' AND next_fire_time <= NOW() "
            + "ORDER BY next_fire_time, id LIMIT #{limit}")
    List<WorkflowTrigger> selectDueSchedules(@Param("limit") int limit);

    @Update("UPDATE ai_workflow_trigger SET next_fire_time = #{nextFireTime}, "
            + "last_fire_time = NOW(), lock_version = lock_version + 1, update_time = NOW() "
            + "WHERE trigger_id = #{triggerId} AND status = 'ACTIVE' "
            + "AND trigger_type = 'SCHEDULE' AND next_fire_time <= NOW()")
    int claimSchedule(
            @Param("triggerId") String triggerId,
            @Param("nextFireTime") Date nextFireTime);

    @Update("UPDATE ai_workflow_trigger SET last_execution_id = #{executionId}, "
            + "last_trigger_status = #{status}, last_error_message = #{errorMessage}, "
            + "update_time = NOW() WHERE trigger_id = #{triggerId}")
    int recordTriggerResult(
            @Param("triggerId") String triggerId,
            @Param("executionId") String executionId,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage);
}
