package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowTriggerFire;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;

/** 工作流定时触发可靠批次的数据访问接口。 */
@Mapper
public interface WorkflowTriggerFireMapper extends BaseMapper<WorkflowTriggerFire> {

    @Insert("INSERT INTO ai_workflow_trigger_fire "
            + "(tenant_id, fire_id, trigger_id, scheduled_time, fire_status, attempt_count, "
            + "create_time, update_time) VALUES "
            + "(#{tenantId}, #{fireId}, #{triggerId}, #{scheduledTime}, 'PENDING', 0, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE fire_id = fire_id")
    int insertPending(
            @Param("tenantId") Long tenantId,
            @Param("fireId") String fireId,
            @Param("triggerId") String triggerId,
            @Param("scheduledTime") Date scheduledTime);

    @Select("SELECT fire_id FROM ai_workflow_trigger_fire WHERE attempt_count < #{maxAttempts} AND ("
            + "fire_status = 'PENDING' "
            + "OR (fire_status = 'FAILED' AND next_retry_time <= NOW()) "
            + "OR (fire_status = 'DISPATCHING' AND claim_until < NOW())) "
            + "ORDER BY scheduled_time, id LIMIT #{limit}")
    List<String> selectDispatchCandidates(
            @Param("limit") int limit,
            @Param("maxAttempts") int maxAttempts);

    @Update("UPDATE ai_workflow_trigger_fire SET fire_status = 'DISPATCHING', "
            + "claimed_by = #{publisherId}, "
            + "claim_until = DATE_ADD(NOW(), INTERVAL #{leaseSeconds} SECOND), "
            + "attempt_count = attempt_count + 1, update_time = NOW() "
            + "WHERE fire_id = #{fireId} AND attempt_count < #{maxAttempts} AND ("
            + "fire_status = 'PENDING' "
            + "OR (fire_status = 'FAILED' AND next_retry_time <= NOW()) "
            + "OR (fire_status = 'DISPATCHING' AND claim_until < NOW()))")
    int claim(
            @Param("fireId") String fireId,
            @Param("publisherId") String publisherId,
            @Param("leaseSeconds") int leaseSeconds,
            @Param("maxAttempts") int maxAttempts);

    @Select("SELECT * FROM ai_workflow_trigger_fire WHERE fire_id = #{fireId} LIMIT 1")
    WorkflowTriggerFire selectByFireId(@Param("fireId") String fireId);

    @Update("UPDATE ai_workflow_trigger_fire SET fire_status = 'DISPATCHED', "
            + "execution_id = #{executionId}, error_message = NULL, dispatched_time = NOW(), "
            + "claimed_by = NULL, claim_until = NULL, next_retry_time = NULL, update_time = NOW() "
            + "WHERE fire_id = #{fireId} AND claimed_by = #{publisherId} "
            + "AND fire_status = 'DISPATCHING'")
    int markDispatched(
            @Param("fireId") String fireId,
            @Param("publisherId") String publisherId,
            @Param("executionId") String executionId);

    @Update("UPDATE ai_workflow_trigger_fire SET fire_status = #{status}, "
            + "error_message = #{errorMessage}, "
            + "next_retry_time = CASE WHEN #{status} = 'FAILED' "
            + "THEN DATE_ADD(NOW(), INTERVAL #{delaySeconds} SECOND) ELSE NULL END, "
            + "claimed_by = NULL, claim_until = NULL, update_time = NOW() "
            + "WHERE fire_id = #{fireId} AND claimed_by = #{publisherId} "
            + "AND fire_status = 'DISPATCHING'")
    int markFailed(
            @Param("fireId") String fireId,
            @Param("publisherId") String publisherId,
            @Param("status") String status,
            @Param("delaySeconds") int delaySeconds,
            @Param("errorMessage") String errorMessage);

    @Update("UPDATE ai_workflow_trigger_fire SET fire_status = 'CANCELLED', "
            + "error_message = #{reason}, claimed_by = NULL, claim_until = NULL, "
            + "next_retry_time = NULL, update_time = NOW() "
            + "WHERE fire_id = #{fireId} AND claimed_by = #{publisherId} "
            + "AND fire_status = 'DISPATCHING'")
    int markCancelled(
            @Param("fireId") String fireId,
            @Param("publisherId") String publisherId,
            @Param("reason") String reason);

    @Update("UPDATE ai_workflow_trigger_fire SET fire_status = 'DEAD', "
            + "error_message = '派发进程退出且重试次数已耗尽', claimed_by = NULL, "
            + "claim_until = NULL, next_retry_time = NULL, update_time = NOW() "
            + "WHERE attempt_count >= #{maxAttempts} AND ("
            + "fire_status = 'FAILED' "
            + "OR (fire_status = 'DISPATCHING' AND claim_until < NOW()))")
    int markExpiredExhausted(@Param("maxAttempts") int maxAttempts);
}
