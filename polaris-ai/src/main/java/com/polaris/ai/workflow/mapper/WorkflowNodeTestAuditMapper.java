package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowNodeTestAudit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/** 单节点试运行审计摘要数据访问接口。 */
@Mapper
public interface WorkflowNodeTestAuditMapper extends BaseMapper<WorkflowNodeTestAudit> {

    @Update("UPDATE ai_workflow_node_test_audit SET outcome_status = #{status}, "
            + "usage_json = #{usageJson}, total_tokens = #{totalTokens}, "
            + "cost_amount = #{costAmount}, attempt_count = #{attemptCount}, "
            + "error_code = #{errorCode}, duration_ms = #{durationMs}, finish_time = NOW() "
            + "WHERE test_run_id = #{testRunId} AND outcome_status IS NULL")
    int finish(
            @Param("testRunId") String testRunId,
            @Param("status") String status,
            @Param("usageJson") String usageJson,
            @Param("totalTokens") Long totalTokens,
            @Param("costAmount") BigDecimal costAmount,
            @Param("attemptCount") int attemptCount,
            @Param("errorCode") String errorCode,
            @Param("durationMs") long durationMs);

    @Update("UPDATE ai_workflow_node_test_audit SET outcome_status = 'CANCELLED', "
            + "attempt_count = (SELECT attempt_count FROM ai_workflow_node_test_run "
            + "WHERE test_run_id = #{testRunId}), error_code = 'NODE_CANCELLED', "
            + "duration_ms = 0, finish_time = NOW() "
            + "WHERE test_run_id = #{testRunId} AND outcome_status IS NULL")
    int cancel(@Param("testRunId") String testRunId);
}
