package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowNodeTestRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 单节点隔离试运行记录数据访问接口。 */
@Mapper
public interface WorkflowNodeTestRunMapper extends BaseMapper<WorkflowNodeTestRun> {

    @Select("SELECT * FROM ai_workflow_node_test_run WHERE test_run_id = #{testRunId}")
    WorkflowNodeTestRun selectByTestRunId(@Param("testRunId") String testRunId);

    @Select("SELECT * FROM ai_workflow_node_test_run WHERE definition_id = #{definitionId} "
            + "AND node_id = #{nodeId} AND environment = #{environment} "
            + "AND principal_type = #{principalType} AND principal_id = #{principalId} "
            + "AND node_config_hash = #{nodeConfigHash} "
            + "AND schema_source_version <=> #{schemaSourceVersion} "
            + "AND status = 'SUCCEEDED' AND output_json IS NOT NULL "
            + "ORDER BY finish_time DESC, id DESC LIMIT #{limit}")
    List<WorkflowNodeTestRun> selectCompatibleSuccessfulSamples(
            @Param("definitionId") Long definitionId,
            @Param("nodeId") String nodeId,
            @Param("environment") String environment,
            @Param("principalType") String principalType,
            @Param("principalId") String principalId,
            @Param("nodeConfigHash") String nodeConfigHash,
            @Param("schemaSourceVersion") String schemaSourceVersion,
            @Param("limit") int limit);

    @Select("SELECT test_run_id FROM ai_workflow_node_test_run "
            + "WHERE cancel_requested = 0 AND (status = 'QUEUED' "
            + "OR (status = 'RUNNING' AND lease_until < NOW())) "
            + "ORDER BY CASE WHEN status = 'QUEUED' THEN 0 ELSE 1 END, create_time, id "
            + "LIMIT #{limit}")
    List<String> selectClaimCandidates(@Param("limit") int limit);

    @Update("UPDATE ai_workflow_node_test_run SET status = 'RUNNING', "
            + "runner_id = #{runnerId}, fencing_token = fencing_token + 1, "
            + "attempt_count = attempt_count + 1, "
            + "lease_until = DATE_ADD(NOW(), INTERVAL #{leaseSeconds} SECOND), "
            + "heartbeat_time = NOW(), start_time = COALESCE(start_time, NOW()) "
            + "WHERE test_run_id = #{testRunId} AND cancel_requested = 0 "
            + "AND (status = 'QUEUED' OR (status = 'RUNNING' AND lease_until < NOW()))")
    int claimLease(
            @Param("testRunId") String testRunId,
            @Param("runnerId") String runnerId,
            @Param("leaseSeconds") int leaseSeconds);

    @Update("UPDATE ai_workflow_node_test_run SET "
            + "lease_until = DATE_ADD(NOW(), INTERVAL #{leaseSeconds} SECOND), "
            + "heartbeat_time = NOW() WHERE test_run_id = #{testRunId} "
            + "AND runner_id = #{runnerId} AND fencing_token = #{fencingToken} "
            + "AND status = 'RUNNING' AND cancel_requested = 0")
    int renewLease(
            @Param("testRunId") String testRunId,
            @Param("runnerId") String runnerId,
            @Param("fencingToken") long fencingToken,
            @Param("leaseSeconds") int leaseSeconds);

    @Update("UPDATE ai_workflow_node_test_run SET status = 'QUEUED', "
            + "runner_id = NULL, lease_until = NULL, heartbeat_time = NOW() "
            + "WHERE test_run_id = #{testRunId} AND runner_id = #{runnerId} "
            + "AND fencing_token = #{fencingToken} AND status = 'RUNNING' "
            + "AND cancel_requested = 0")
    int releaseLease(
            @Param("testRunId") String testRunId,
            @Param("runnerId") String runnerId,
            @Param("fencingToken") long fencingToken);

    @Update("UPDATE ai_workflow_node_test_run SET cancel_requested = 1, status = 'CANCELLED', "
            + "error_code = 'NODE_CANCELLED', error_message = '单节点试运行已取消', "
            + "payload_ciphertext = NULL, runner_id = NULL, lease_until = NULL, "
            + "finish_time = NOW() WHERE test_run_id = #{testRunId} "
            + "AND status IN ('QUEUED','RUNNING')")
    int requestCancel(@Param("testRunId") String testRunId);

    @Update("UPDATE ai_workflow_node_test_run SET status = #{status}, input_json = #{inputJson}, "
            + "output_json = #{outputJson}, "
            + "usage_json = #{usageJson}, schema_source = #{schemaSource}, "
            + "schema_source_version = #{schemaSourceVersion}, "
            + "schema_diagnostics_json = #{schemaDiagnosticsJson}, error_code = #{errorCode}, "
            + "error_message = #{errorMessage}, duration_ms = #{durationMs}, "
            + "payload_ciphertext = NULL, runner_id = NULL, lease_until = NULL, "
            + "heartbeat_time = NOW(), finish_time = NOW() "
            + "WHERE test_run_id = #{testRunId} AND runner_id = #{runnerId} "
            + "AND fencing_token = #{fencingToken} AND status = 'RUNNING' "
            + "AND cancel_requested = 0")
    int finish(
            @Param("testRunId") String testRunId,
            @Param("runnerId") String runnerId,
            @Param("fencingToken") long fencingToken,
            @Param("status") String status,
            @Param("inputJson") String inputJson,
            @Param("outputJson") String outputJson,
            @Param("usageJson") String usageJson,
            @Param("schemaSource") String schemaSource,
            @Param("schemaSourceVersion") String schemaSourceVersion,
            @Param("schemaDiagnosticsJson") String schemaDiagnosticsJson,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("durationMs") long durationMs);

}
