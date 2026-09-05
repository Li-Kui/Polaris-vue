package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 支持租约和隔离令牌操作的工作流持久化执行数据访问接口。 */
@Mapper
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecution> {

    @Update("UPDATE ai_workflow_execution SET resume_time = NOW() "
            + "WHERE execution_id = #{executionId} AND status = 'WAITING_EVENT' AND cancel_requested = 0")
    int wakeForChild(@Param("executionId") String executionId);

    @Select("SELECT * FROM ai_workflow_execution WHERE execution_id = #{executionId}")
    WorkflowExecution selectByExecutionId(@Param("executionId") String executionId);

    @Select("SELECT * FROM ai_workflow_execution WHERE execution_id = #{executionId} FOR UPDATE")
    WorkflowExecution selectByExecutionIdForUpdate(@Param("executionId") String executionId);

    @Select("SELECT * FROM ai_workflow_execution "
            + "WHERE idempotency_scope = #{scope} AND idempotency_key = #{key} LIMIT 1")
    WorkflowExecution selectByIdempotency(
            @Param("scope") String scope, @Param("key") String key);

    @Select("SELECT execution_id FROM ai_workflow_execution "
            + "WHERE cancel_requested = 0 AND (status = 'QUEUED' "
            + "OR (status = 'WAITING_EVENT' AND resume_time <= NOW()) "
            + "OR (status IN ('RUNNING','RECOVERING') AND lease_until < NOW())) "
            + "ORDER BY CASE WHEN status IN ('QUEUED','WAITING_EVENT') THEN 0 ELSE 1 END, create_time, id LIMIT #{limit}")
    List<String> selectClaimCandidates(@Param("limit") int limit);

    @Update("UPDATE ai_workflow_execution SET "
            + "recovery_count = recovery_count + CASE WHEN status IN ('QUEUED','WAITING_EVENT') THEN 0 ELSE 1 END, "
            + "status = CASE WHEN status IN ('QUEUED','WAITING_EVENT') THEN 'RUNNING' ELSE 'RECOVERING' END, "
            + "runner_id = #{runnerId}, fencing_token = fencing_token + 1, "
            + "lease_until = DATE_ADD(NOW(), INTERVAL #{leaseSeconds} SECOND), "
            + "resume_time = NULL, "
            + "heartbeat_time = NOW(), start_time = COALESCE(start_time, NOW()) "
            + "WHERE execution_id = #{executionId} AND cancel_requested = 0 AND "
            + "(status = 'QUEUED' OR (status = 'WAITING_EVENT' AND resume_time <= NOW()) "
            + "OR (status IN ('RUNNING','RECOVERING') AND lease_until < NOW()))")
    int claimLease(
            @Param("executionId") String executionId,
            @Param("runnerId") String runnerId,
            @Param("leaseSeconds") int leaseSeconds);

    @Update("UPDATE ai_workflow_execution SET lease_until = DATE_ADD(NOW(), "
            + "INTERVAL #{leaseSeconds} SECOND), heartbeat_time = NOW() "
            + "WHERE execution_id = #{executionId} AND runner_id = #{runnerId} "
            + "AND fencing_token = #{fencingToken} AND status IN ('RUNNING','RECOVERING')")
    int renewLease(
            @Param("executionId") String executionId,
            @Param("runnerId") String runnerId,
            @Param("fencingToken") long fencingToken,
            @Param("leaseSeconds") int leaseSeconds);

    @Update("UPDATE ai_workflow_execution SET cancel_requested = 1 "
            + "WHERE execution_id = #{executionId} "
            + "AND status NOT IN ('SUCCEEDED','FAILED','CANCELLED','REJECTED')")
    int requestCancel(@Param("executionId") String executionId);

    @Update("UPDATE ai_workflow_execution SET cancel_requested = 1, status = 'CANCELLED', "
            + "finish_time = NOW(), lease_until = NULL, runner_id = NULL "
            + "WHERE execution_id = #{executionId} AND status = 'QUEUED'")
    int cancelBeforeRun(@Param("executionId") String executionId);

    @Update("UPDATE ai_workflow_execution SET cancel_requested = 1, status = 'CANCELLED', "
            + "finish_time = NOW(), lease_until = NULL, resume_time = NULL, runner_id = NULL "
            + "WHERE execution_id = #{executionId} "
            + "AND status IN ('WAITING_APPROVAL','WAITING_EVENT','NEEDS_ATTENTION')")
    int cancelWhileWaiting(@Param("executionId") String executionId);

    @Update("UPDATE ai_workflow_execution SET status = 'QUEUED', runner_id = NULL, "
            + "lease_until = NULL, resume_time = NULL, heartbeat_time = NOW() "
            + "WHERE execution_id = #{executionId} AND status = 'WAITING_APPROVAL' "
            + "AND cancel_requested = 0")
    int requeueAfterApproval(@Param("executionId") String executionId);

    @Update("UPDATE ai_workflow_execution SET status = 'REJECTED', "
            + "error_code = #{errorCode}, error_message = #{errorMessage}, "
            + "finish_time = NOW(), runner_id = NULL, lease_until = NULL, resume_time = NULL "
            + "WHERE execution_id = #{executionId} AND status = 'WAITING_APPROVAL'")
    int rejectAfterApproval(
            @Param("executionId") String executionId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);

    @Update("UPDATE ai_workflow_execution SET quota_released = 1 "
            + "WHERE execution_id = #{executionId} AND quota_released = 0")
    int markQuotaReleased(@Param("executionId") String executionId);

    @Update("UPDATE ai_workflow_execution SET status = #{status}, output_json = #{outputJson}, "
            + "error_code = #{errorCode}, error_message = #{errorMessage}, finish_time = NOW(), "
            + "runner_id = NULL, lease_until = NULL, heartbeat_time = NOW() "
            + "WHERE execution_id = #{executionId} AND runner_id = #{runnerId} "
            + "AND fencing_token = #{fencingToken} AND status IN ('RUNNING','RECOVERING')")
    int finishWithFence(
            @Param("executionId") String executionId,
            @Param("runnerId") String runnerId,
            @Param("fencingToken") long fencingToken,
            @Param("status") String status,
            @Param("outputJson") String outputJson,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);
}
