package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.polaris.ai.agent.collaboration.AgentRunCallRow;
import com.polaris.ai.agent.collaboration.AgentRunRow;
import com.polaris.ai.workflow.domain.WorkflowExecution;
import com.polaris.ai.workflow.domain.WorkflowNodeRun;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AgentRunMapper {
    @Select("SELECT * FROM ai_agent_run WHERE tenant_id=#{tenantId} AND run_id=#{runId}")
    AgentRunRow get(@Param("tenantId") Long tenant, @Param("runId") String run);
    @Select("SELECT * FROM ai_agent_run WHERE tenant_id=#{tenantId} AND run_id=#{runId} FOR UPDATE")
    AgentRunRow lock(@Param("tenantId") Long tenant, @Param("runId") String run);
    @Select("SELECT * FROM ai_workflow_execution WHERE tenant_id=#{tenantId} AND execution_id=#{executionId}")
    WorkflowExecution execution(@Param("tenantId") Long tenant, @Param("executionId") String execution);
    @Select("SELECT * FROM ai_workflow_node_run WHERE tenant_id=#{tenantId} AND execution_id=#{executionId} AND node_run_id=#{nodeRunId} AND attempt_no=1")
    WorkflowNodeRun node(@Param("tenantId") Long tenant, @Param("executionId") String execution, @Param("nodeRunId") String node);
    @Insert("INSERT INTO ai_agent_run(run_id,tenant_id,actor_user_id,agent_id,execution_id,node_run_id,attempt_no,workflow_code,runner_id,fencing_token,definition_hash,model_id,model_hash,request_hash,status,stage,max_calls,deadline,started_time) "
        + "VALUES(#{runId},#{tenantId},#{actorUserId},#{agentId},#{executionId},#{nodeRunId},#{attemptNo},#{workflowCode},#{runnerId},#{fencingToken},#{definitionHash},#{modelId},#{modelHash},#{requestHash},'RUNNING',#{stage},#{maxCalls},#{deadline},#{startedTime})")
    int insert(AgentRunRow row);
    @Update("UPDATE ai_agent_run SET stage=#{stage},tool_count=#{toolCount},plan_count=#{planCount},answer_count=#{answerCount},plan_usage_recorded=#{planUsageRecorded},answer_usage_recorded=#{answerUsageRecorded},"
        + "plan_input_tokens=#{planInputTokens},plan_output_tokens=#{planOutputTokens},plan_total_tokens=#{planTotalTokens},answer_input_tokens=#{answerInputTokens},answer_output_tokens=#{answerOutputTokens},answer_total_tokens=#{answerTotalTokens},"
        + "status=#{status},error_code=#{errorCode},dispatch=#{dispatch},finished_time=#{finishedTime} WHERE tenant_id=#{tenantId} AND run_id=#{runId} AND status='RUNNING' AND fencing_token=#{fencingToken}")
    int update(AgentRunRow row);
    @Insert("INSERT INTO ai_agent_run_call(tenant_id,run_id,call_no,tool_id,tool_revision,connection_revision,catalog_revision,structure_hash,arguments_hash) "
        + "VALUES(#{tenantId},#{runId},#{callNo},#{toolId},#{toolRevision},#{connectionRevision},#{catalogRevision},#{structureHash},#{argumentsHash})")
    int insertCall(AgentRunCallRow row);
    @Select("SELECT * FROM ai_agent_run_call WHERE tenant_id=#{tenantId} AND run_id=#{runId} ORDER BY call_no")
    List<AgentRunCallRow> calls(@Param("tenantId") Long tenant, @Param("runId") String run);
    @Update("UPDATE ai_agent_run_call SET status=#{status},invocation_id=#{invocationId},dispatch=#{dispatch} WHERE tenant_id=#{tenantId} AND run_id=#{runId} AND call_no=#{callNo} AND status='RESERVED'")
    int finishCall(AgentRunCallRow row);
    @Select("SELECT * FROM ai_agent_run WHERE tenant_id=#{tenantId} AND execution_id=#{executionId} AND node_run_id=#{nodeRunId} ORDER BY started_time DESC LIMIT 1")
    AgentRunRow forNode(@Param("tenantId") Long tenant, @Param("executionId") String execution, @Param("nodeRunId") String node);
    @Select("SELECT * FROM ai_workflow_definition WHERE tenant_id=#{tenantId} AND del_flag='0' ORDER BY id LIMIT 201")
    List<com.polaris.ai.workflow.domain.WorkflowDefinition> impactCandidates(@Param("tenantId") Long tenant);
}
