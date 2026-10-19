package com.polaris.ai.agent.collaboration;

import com.polaris.common.exception.ServiceException;

import java.util.Date;
import java.util.List;

/** 聊天请求不接受主体、围栏、任意 messages 或客户端冻结资源。 */
public final class AgentChatContracts {
    private AgentChatContracts() { }
    public static final String POLICY = "agent-chat-g3.1-v1";
    public static final int MAX_TURNS = 6, MAX_HISTORY_CHARS = 16000, MAX_DEPENDENCIES = 50;
    public static final int MAX_WAIT_SECONDS = 90, LEASE_SECONDS = 15, HEARTBEAT_SECONDS = 3;
    public enum AnswerMode { QUERY_EVIDENCE, CONTINUE_HISTORY }
    public enum Status { PREPARED, RUNNING, SUCCEEDED, FAILED, CANCELLED, NEEDS_ATTENTION, EXPIRED }
    public record Scope(Long tenantId, Long actorUserId, Long conversationId) {
        public Scope { positive(tenantId); positive(actorUserId); positive(conversationId); }
    }
    public record Input(Long conversationId, String requestKey, Long agentId, String question,
            Long knowledgeBaseId, String externalQuery, boolean allowExternalTools, AnswerMode answerMode,
            Long contextStartMessageId, boolean inheritPreviousContext) {
        public Input {
            positive(conversationId); positive(agentId);
            if (requestKey == null || !requestKey.matches("[A-Za-z0-9_.:-]{1,64}")
                    || question == null || question.isBlank() || question.length() > 4000
                    || answerMode == null || contextStartMessageId != null && contextStartMessageId <= 0
                    || knowledgeBaseId != null && knowledgeBaseId <= 0) throw invalid();
            externalQuery = externalQuery == null ? "" : externalQuery;
            if (externalQuery.length() > 4000 || allowExternalTools && externalQuery.isBlank()
                    || !allowExternalTools && !externalQuery.isEmpty()
                    || answerMode == AnswerMode.CONTINUE_HISTORY && allowExternalTools) throw invalid();
        }
        @Override public String toString() { return "AgentChatInput[conversationId=" + conversationId + ", mode=" + answerMode + "]"; }
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String key,Object value) {throw invalid();}
    }
    public record KnowledgeDependency(Long knowledgeId, Long documentId, Long indexVersion, String collectionHash) {
        public KnowledgeDependency {
            positive(knowledgeId); positive(documentId); positive(indexVersion); hash(collectionHash);
        }
    }
    public record ToolDependency(String invocationId, String runId, int callNo) {
        public ToolDependency {
            id(invocationId); id(runId); if (callNo < 1 || callNo > 2) throw invalid();
        }
    }
    public record HistoricalReference(Long messageId,String citationId) {
        public HistoricalReference {
            positive(messageId);if(citationId==null || !citationId.matches("S[1-9][0-9]{0,2}|T[12]"))throw invalid();
        }
    }
    public record Dependencies(int version, List<KnowledgeDependency> knowledge, List<ToolDependency> tools,List<HistoricalReference> history) {
        public Dependencies(int version,List<KnowledgeDependency> knowledge,List<ToolDependency> tools) {this(version,knowledge,tools,List.of());}
        public Dependencies {
            history=history==null?List.of():List.copyOf(history);
            if (version != 1 || knowledge == null || tools == null || knowledge.size() + tools.size()+history.size() > MAX_DEPENDENCIES)
                throw invalid();
            knowledge = List.copyOf(knowledge); tools = List.copyOf(tools);
        }
        public static Dependencies empty() { return new Dependencies(1, List.of(), List.of()); }
    }
    public record TurnRef(Long userMessageId, Long assistantMessageId, String userHash, String assistantHash,
            String dependenciesHash, Dependencies dependencies) { }
    public record HistorySnapshot(long watermark, long startMessageId, List<TurnRef> turns,
            int excludedTurns, boolean truncated) {
        public HistorySnapshot { turns = List.copyOf(turns); }
    }
    public record HistoryMessage(Long messageId, String role, String text) {
        @Override public String toString() { return "HistoryMessage[id=" + messageId + ", role=" + role + "]"; }
    }
    public record History(HistorySnapshot snapshot, List<HistoryMessage> messages, String retrievalContext) {
        public History { messages = List.copyOf(messages); }
        @Override public String toString() { return "AgentChatHistory[turns=" + snapshot.turns().size() + "]"; }
    }
    public record ToolTarget(Long toolId, String alias, String label, String purpose, String fixedArgumentsJson) { }
    public record InputProof(String questionHash,String externalQueryHash) {
        public InputProof {hash(questionHash);hash(externalQueryHash);}
    }
    public record ResourceSnapshot(String policy, Long agentRevision, String agentHash, Long modelId,
            String modelHash, String knowledgeHash, List<BoundedAgentContracts.ToolSnapshot> tools, List<ToolTarget> targets,InputProof proof) {
        public ResourceSnapshot { tools = List.copyOf(tools); targets = List.copyOf(targets); }
    }
    public record PreparedView(String requestId, String status, String answerMode, Long agentId, Long modelId,
            int maxCalls, int maxWaitSeconds, int historyTurns, boolean historyTruncated, int excludedTurns,
            List<Long> historyMessageIds, List<ToolTarget> toolTargets, Date expiresAt, String runId, Long userMessageId, Long assistantMessageId,
            boolean cancelRequested, String errorCode,RunSummary run) { }
    public record RunUsage(Long inputTokens,Long outputTokens,Long totalTokens) { }
    public record CallSummary(Integer callNo,Long toolId,String status,String invocationId,String dispatch) { }
    public record RunSummary(Integer planCount,Integer answerCount,Integer toolCount,String stage,String dispatch,
            RunUsage planningUsage,RunUsage answerUsage,List<CallSummary> calls) { }
    /** 仅供服务端执行适配器持有，不通过 Controller 返回 ownerToken。 */
    public record Permit(Scope scope, AgentEntryIdentity.Chat identity, String runId, String ownerToken, Date deadline) {
        @Override public String toString() { return "AgentChatPermit[requestId=" + identity.requestId() + "]"; }
    }
    public static String digest(Object value) {
        return AgentBindingService.digest(AgentPlanValidator.JSON.writeValueAsString(
                AgentPlanValidator.sorted(AgentPlanValidator.JSON.valueToTree(value))));
    }
    public static void positive(Long value) { if (value == null || value <= 0) throw invalid(); }
    public static void id(String value) { if (value == null || !value.matches("[A-Za-z0-9_.:-]{1,64}")) throw invalid(); }
    public static void hash(String value) { if (value == null || !value.matches("[a-f0-9]{64}")) throw invalid(); }
    public static ServiceException invalid() { return new ServiceException("协作请求不完整或超出限制", 400); }
    public static ServiceException conflict() { return new ServiceException("请求、上下文或资源已变化，请重新确认", 409); }
    public static ServiceException denied() { return new ServiceException("会话不存在或当前主体无权使用协作", 403); }
}
