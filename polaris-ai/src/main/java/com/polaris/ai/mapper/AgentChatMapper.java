package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.polaris.ai.agent.collaboration.*;
import com.polaris.ai.agent.collaboration.AgentChatContracts.Scope;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;

/** 所有读取/写入显式限制租户与真实会话持有人，不依赖可缺省的租户插件。 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AgentChatMapper {
    @Select("SELECT CURRENT_TIMESTAMP(3)") Date now();

    @Select("SELECT id,tenant_id,user_id,collaboration_context_key,collaboration_context_start_id FROM ai_conversation WHERE tenant_id=#{tenantId} AND user_id=#{actorUserId} AND id=#{conversationId} AND del_flag='0' FOR UPDATE")
    AgentChatConversationRow lockConversation(Scope scope);
    @Select("SELECT id,tenant_id,user_id,collaboration_context_key,collaboration_context_start_id FROM ai_conversation WHERE tenant_id=#{tenantId} AND user_id=#{actorUserId} AND id=#{conversationId} FOR UPDATE")
    AgentChatConversationRow lockAnyConversation(Scope scope);

    @Select("SELECT * FROM ai_agent_chat_request WHERE tenant_id=#{scope.tenantId} AND actor_user_id=#{scope.actorUserId} AND conversation_id=#{scope.conversationId} AND request_key=#{key}")
    AgentChatRequestRow byKey(@Param("scope") Scope scope, @Param("key") String key);
    @Select("SELECT * FROM ai_agent_chat_request WHERE tenant_id=#{tenantId} AND actor_user_id=#{userId} AND request_id=#{requestId}")
    AgentChatRequestRow get(@Param("tenantId") Long tenant, @Param("userId") Long user, @Param("requestId") String id);
    @Select("SELECT * FROM ai_agent_chat_request WHERE tenant_id=#{tenantId} AND actor_user_id=#{userId} AND run_id=#{runId}")
    AgentChatRequestRow forRun(@Param("tenantId")Long tenant,@Param("userId")Long user,@Param("runId")String runId);
    @Select("SELECT * FROM ai_agent_chat_request WHERE tenant_id=#{scope.tenantId} AND actor_user_id=#{scope.actorUserId} AND conversation_id=#{scope.conversationId} AND request_id=#{id} FOR UPDATE")
    AgentChatRequestRow lock(@Param("scope") Scope scope, @Param("id") String id);
    @Insert("""
        INSERT INTO ai_agent_chat_request(request_id,tenant_id,actor_user_id,conversation_id,request_key,input_hash,snapshot_hash,snapshot_json,
          history_hash,history_json,context_key,context_start_message_id,history_watermark,answer_mode,agent_id,model_id,knowledge_base_id,
          max_calls,max_wait_seconds,status,expires_at,created_time)
        VALUES(#{requestId},#{tenantId},#{actorUserId},#{conversationId},#{requestKey},#{inputHash},#{snapshotHash},#{snapshotJson},
          #{historyHash},#{historyJson},#{contextKey},#{contextStartMessageId},#{historyWatermark},#{answerMode},#{agentId},#{modelId},#{knowledgeBaseId},
          #{maxCalls},#{maxWaitSeconds},'PREPARED',#{expiresAt},#{createdTime})
        """)
    int insert(AgentChatRequestRow row);
    @Update("""
        UPDATE ai_agent_chat_request SET status=#{r.status},run_id=#{r.runId},owner_token=#{r.ownerToken},lease_until=#{r.leaseUntil},
          user_message_id=#{r.userMessageId},assistant_message_id=#{r.assistantMessageId},cancel_requested_at=#{r.cancelRequestedAt},
          deadline=#{r.deadline},started_time=#{r.startedTime},finished_time=#{r.finishedTime},error_code=#{r.errorCode}
        WHERE tenant_id=#{r.tenantId} AND actor_user_id=#{r.actorUserId} AND conversation_id=#{r.conversationId} AND request_id=#{r.requestId}
          AND status=#{expectedStatus} AND (owner_token=#{expectedToken} OR (owner_token IS NULL AND #{expectedToken} IS NULL))
        """)
    int transition(@Param("r") AgentChatRequestRow row, @Param("expectedStatus") String status, @Param("expectedToken") String token);
    @Update("""
        UPDATE ai_agent_chat_request SET lease_until=#{leaseUntil}
        WHERE tenant_id=#{scope.tenantId} AND actor_user_id=#{scope.actorUserId} AND conversation_id=#{scope.conversationId}
          AND request_id=#{id} AND owner_token=#{token} AND status='RUNNING' AND cancel_requested_at IS NULL
          AND lease_until>CURRENT_TIMESTAMP(3) AND deadline>CURRENT_TIMESTAMP(3)
        """)
    int renew(@Param("scope") Scope scope, @Param("id") String id, @Param("token") String token, @Param("leaseUntil") Date until);

    @Select("""
        SELECT m.id,m.conversation_id,m.role,m.content,m.moderation_status,m.agent_run_id,m.chat_context_key,m.history_dependencies,
          q.status AS generation_status,
          CASE WHEN COALESCE(m.file_url,'')<>'' OR COALESCE(m.file_content,'')<>'' OR COALESCE(m.attachment_tokens,'')<>''
            OR m.workflow_execution_id IS NOT NULL THEN TRUE ELSE FALSE END AS has_attachments
        FROM ai_message m JOIN ai_conversation c ON c.id=m.conversation_id
        LEFT JOIN ai_agent_chat_request q ON q.tenant_id=c.tenant_id AND q.actor_user_id=c.user_id
          AND q.conversation_id=c.id AND q.run_id=m.agent_run_id
        WHERE c.tenant_id=#{tenantId} AND c.user_id=#{actorUserId} AND c.id=#{conversationId} AND c.del_flag='0'
        ORDER BY m.id DESC LIMIT 100
        """)
    List<AgentChatHistoryRow> history(Scope scope);
    @Select("""
        SELECT m.id,m.conversation_id,m.role,m.content,m.moderation_status,m.agent_run_id,m.chat_context_key,m.history_dependencies,
          q.status AS generation_status,
          CASE WHEN COALESCE(m.file_url,'')<>'' OR COALESCE(m.file_content,'')<>'' OR COALESCE(m.attachment_tokens,'')<>''
            OR m.workflow_execution_id IS NOT NULL THEN TRUE ELSE FALSE END AS has_attachments
        FROM ai_message m JOIN ai_conversation c ON c.id=m.conversation_id
        LEFT JOIN ai_agent_chat_request q ON q.tenant_id=c.tenant_id AND q.actor_user_id=c.user_id AND q.conversation_id=c.id AND q.run_id=m.agent_run_id
        WHERE c.tenant_id=#{scope.tenantId} AND c.user_id=#{scope.actorUserId} AND c.id=#{scope.conversationId} AND c.del_flag='0' AND m.id=#{id}
        """)
    AgentChatHistoryRow message(@Param("scope") Scope scope,@Param("id") Long id);
    @Update("""
        UPDATE ai_agent_run SET request_hash=#{hash},stage='CLAIMED' WHERE tenant_id=#{scope.tenantId} AND actor_user_id=#{scope.actorUserId}
          AND conversation_id=#{scope.conversationId} AND run_id=#{runId} AND entry_type='CHAT' AND status='RUNNING'
          AND stage IN ('KNOWLEDGE','PLAN','ANSWER') AND plan_count=0 AND tool_count=0 AND answer_count=0
        """)
    int claimRun(@Param("scope") Scope scope,@Param("runId") String runId,@Param("hash") String hash);
    @Update("UPDATE ai_agent_run SET stage=#{stage} WHERE tenant_id=#{scope.tenantId} AND actor_user_id=#{scope.actorUserId} AND conversation_id=#{scope.conversationId} AND run_id=#{runId} AND entry_type='CHAT' AND status='RUNNING' AND stage='INPUT' AND plan_count=0 AND answer_count=0 AND tool_count=0")
    int admitRun(@Param("scope") Scope scope,@Param("runId") String runId,@Param("stage") String stage);
    @Update("""
        UPDATE ai_agent_run SET stage=#{stage},tool_count=#{toolCount},plan_count=#{planCount},answer_count=#{answerCount},
          plan_usage_recorded=#{planUsageRecorded},answer_usage_recorded=#{answerUsageRecorded},plan_input_tokens=#{planInputTokens},
          plan_output_tokens=#{planOutputTokens},plan_total_tokens=#{planTotalTokens},answer_input_tokens=#{answerInputTokens},
          answer_output_tokens=#{answerOutputTokens},answer_total_tokens=#{answerTotalTokens},status=#{status},error_code=#{errorCode},dispatch=#{dispatch},finished_time=#{finishedTime}
        WHERE tenant_id=#{tenantId} AND actor_user_id=#{actorUserId} AND run_id=#{runId} AND entry_type='CHAT' AND status='RUNNING'
        """)
    int updateRun(AgentRunRow row);
    @Insert("""
        INSERT INTO ai_message(conversation_id,role,content,agent_run_id,chat_context_key,history_dependencies,knowledge_citations,
          moderation_status,moderation_version,tokens,create_time)
        VALUES(#{message.conversationId},'assistant',#{message.content},#{message.agentRunId},#{message.chatContextKey},#{message.historyDependencies},#{citations},
          'SAFE',#{moderationVersion},#{tokens},CURRENT_TIMESTAMP(3))
        """)
    @Options(useGeneratedKeys=true,keyProperty="message.id")
    int insertAnswer(@Param("message") AgentChatHistoryRow message,@Param("citations") String citations,@Param("moderationVersion") Long moderationVersion,@Param("tokens") Integer tokens);
    @Update("UPDATE ai_message SET moderation_status='SAFE',moderation_version=#{version} WHERE conversation_id=#{scope.conversationId} AND agent_run_id=#{runId} AND role='user'")
    int approveUser(@Param("scope") Scope scope,@Param("runId") String runId,@Param("version") Long version);
    @Select("SELECT knowledge_citations FROM ai_message WHERE conversation_id=#{scope.conversationId} AND id=#{id}")
    String citations(@Param("scope") Scope scope,@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM ai_message m JOIN ai_conversation c ON c.id=m.conversation_id WHERE c.tenant_id=#{scope.tenantId} AND c.user_id=#{scope.actorUserId} AND c.id=#{scope.conversationId} AND c.del_flag='0' AND m.id=#{id}")
    int ownsMessage(@Param("scope") Scope scope, @Param("id") Long id);

    @Select("SELECT * FROM ai_chat_active_generation WHERE tenant_id=#{tenantId} AND actor_user_id=#{actorUserId} AND conversation_id=#{conversationId} FOR UPDATE")
    ChatGenerationRow active(Scope scope);
    @Insert("INSERT INTO ai_chat_active_generation(tenant_id,actor_user_id,conversation_id,entry_type,generation_id,owner_token,lease_until,deadline) VALUES(#{tenantId},#{actorUserId},#{conversationId},#{entryType},#{generationId},#{ownerToken},#{leaseUntil},#{deadline})")
    int occupy(ChatGenerationRow row);
    @Delete("DELETE FROM ai_chat_active_generation WHERE tenant_id=#{scope.tenantId} AND actor_user_id=#{scope.actorUserId} AND conversation_id=#{scope.conversationId} AND generation_id=#{id} AND owner_token=#{token}")
    int release(@Param("scope") Scope scope, @Param("id") String id, @Param("token") String token);
    @Update("UPDATE ai_chat_active_generation SET lease_until=#{leaseUntil} WHERE tenant_id=#{scope.tenantId} AND actor_user_id=#{scope.actorUserId} AND conversation_id=#{scope.conversationId} AND generation_id=#{id} AND owner_token=#{token} AND cancel_requested_at IS NULL AND lease_until>CURRENT_TIMESTAMP(3) AND deadline>CURRENT_TIMESTAMP(3)")
    int renewGeneration(@Param("scope") Scope scope, @Param("id") String id, @Param("token") String token, @Param("leaseUntil") Date until);
    @Update("UPDATE ai_chat_active_generation SET cancel_requested_at=COALESCE(cancel_requested_at,CURRENT_TIMESTAMP(3)) WHERE tenant_id=#{scope.tenantId} AND actor_user_id=#{scope.actorUserId} AND conversation_id=#{scope.conversationId} AND generation_id=#{id}")
    int cancelGeneration(@Param("scope") Scope scope, @Param("id") String id);
    @Update("UPDATE ai_agent_chat_request SET cancel_requested_at=COALESCE(cancel_requested_at,CURRENT_TIMESTAMP(3)) WHERE tenant_id=#{tenantId} AND actor_user_id=#{actorUserId} AND conversation_id=#{conversationId} AND status='RUNNING'")
    int cancelForDeletion(Scope scope);
    @Update("UPDATE ai_conversation SET collaboration_context_key=#{key},collaboration_context_start_id=#{start} WHERE tenant_id=#{scope.tenantId} AND user_id=#{scope.actorUserId} AND id=#{scope.conversationId} AND del_flag='0'")
    int setContext(@Param("scope") Scope scope, @Param("key") String key, @Param("start") long start);
    @Insert("INSERT INTO ai_message(conversation_id,role,content,agent_run_id,chat_context_key,create_time) VALUES(#{conversationId},'user',#{content},#{agentRunId},#{chatContextKey},CURRENT_TIMESTAMP(3))")
    @Options(useGeneratedKeys=true, keyProperty="id")
    int insertUserMessage(AgentChatHistoryRow row);
    @Insert("""
        INSERT INTO ai_agent_run(run_id,tenant_id,actor_user_id,agent_id,entry_type,chat_request_id,conversation_id,
          definition_hash,model_id,model_hash,request_hash,status,stage,max_calls,deadline,started_time)
        VALUES(#{r.runId},#{r.tenantId},#{r.actorUserId},#{r.agentId},'CHAT',#{r.requestId},#{r.conversationId},
          #{agentHash},#{r.modelId},#{modelHash},#{r.inputHash},'RUNNING',#{stage},#{r.maxCalls},#{r.deadline},#{r.startedTime})
        """)
    int insertRun(@Param("r") AgentChatRequestRow row, @Param("agentHash") String agentHash, @Param("modelHash") String modelHash, @Param("stage") String stage);
    @Select("SELECT * FROM ai_agent_run WHERE tenant_id=#{scope.tenantId} AND actor_user_id=#{scope.actorUserId} AND conversation_id=#{scope.conversationId} AND chat_request_id=#{id} AND entry_type='CHAT'")
    AgentRunRow run(@Param("scope") Scope scope, @Param("id") String id);
    @Select("SELECT c.* FROM ai_agent_run_call c JOIN ai_agent_run r ON r.run_id=c.run_id AND r.tenant_id=c.tenant_id WHERE r.tenant_id=#{scope.tenantId} AND r.actor_user_id=#{scope.actorUserId} AND r.conversation_id=#{scope.conversationId} AND r.run_id=#{runId} ORDER BY c.call_no")
    List<AgentRunCallRow> runCalls(@Param("scope") Scope scope,@Param("runId") String runId);
    @Update("UPDATE ai_agent_run SET status='FAILED',error_code=#{code},finished_time=CURRENT_TIMESTAMP(3) WHERE tenant_id=#{scope.tenantId} AND actor_user_id=#{scope.actorUserId} AND conversation_id=#{scope.conversationId} AND chat_request_id=#{id} AND entry_type='CHAT' AND status='RUNNING'")
    int failRun(@Param("scope") Scope scope, @Param("id") String id, @Param("code") String code);
    @Select("""
        SELECT COUNT(*) FROM ai_agent_run r JOIN ai_agent_run_call c ON c.tenant_id=r.tenant_id AND c.run_id=r.run_id
        JOIN platform_mcp_invocation i ON i.tenant_id=c.tenant_id AND i.invocation_id=c.invocation_id
        WHERE r.tenant_id=#{scope.tenantId} AND r.actor_user_id=#{scope.actorUserId} AND r.conversation_id=#{scope.conversationId}
          AND r.entry_type='CHAT' AND r.run_id=#{ref.runId} AND c.call_no=#{ref.callNo} AND c.invocation_id=#{ref.invocationId}
          AND c.status='SUCCESS' AND i.status='SUCCESS' AND i.call_dispatched='1' AND i.source='AGENT_CHAT' AND i.agent_run_id=r.run_id
          AND i.agent_call_no=c.call_no AND i.chat_request_id=r.chat_request_id AND i.conversation_id=r.conversation_id
        """)
    int toolSource(@Param("scope") Scope scope, @Param("ref") AgentChatContracts.ToolDependency ref);
    @Select("""
        SELECT c.call_no,c.tool_id,c.invocation_id,i.status,i.started_time,i.finished_time,t.tool_name,n.connection_name
        FROM ai_agent_run r JOIN ai_agent_run_call c ON c.tenant_id=r.tenant_id AND c.run_id=r.run_id
        JOIN platform_mcp_invocation i ON i.tenant_id=c.tenant_id AND i.invocation_id=c.invocation_id AND i.agent_run_id=r.run_id AND i.agent_call_no=c.call_no
        LEFT JOIN platform_mcp_tool t ON t.tenant_id=c.tenant_id AND t.id=c.tool_id
        LEFT JOIN platform_mcp_connection n ON n.tenant_id=i.tenant_id AND n.id=i.connection_id
        WHERE r.tenant_id=#{scope.tenantId} AND r.actor_user_id=#{scope.actorUserId} AND r.conversation_id=#{scope.conversationId}
          AND r.run_id=#{runId} AND r.entry_type='CHAT' AND c.status='SUCCESS' AND i.status='SUCCESS' AND i.call_dispatched='1'
          AND i.source='AGENT_CHAT' AND i.chat_request_id=r.chat_request_id AND i.conversation_id=r.conversation_id
        ORDER BY c.call_no
        """)
    List<ChatToolSourceRow> toolSources(@Param("scope") Scope scope,@Param("runId")String runId);
}
