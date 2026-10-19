package com.polaris.ai.agent.collaboration;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 有限协作的内部契约；不作为接受客户端身份或资源的请求 DTO。 */
public final class BoundedAgentContracts {
    private BoundedAgentContracts() { }

    public enum Stage { KNOWLEDGE, PLAN, TOOL, ANSWER }
    public enum Dispatch { NOT_DISPATCHED, PREPARED, UNKNOWN }
    public enum Code {
        INVALID_REQUEST, UNSUPPORTED_SCHEMA, INVALID_PLAN, ARGUMENTS_REJECTED,
        ACCESS_DENIED, SNAPSHOT_CHANGED, DUPLICATE_RUN, BUDGET_EXCEEDED,
        CANCELLED, DEADLINE_EXCEEDED, KNOWLEDGE_UNAVAILABLE, INSUFFICIENT_EVIDENCE,
        MODEL_FAILED, MODEL_OUTPUT_INVALID, TOOL_FAILED, AUDIT_INCOMPLETE, RESULT_UNKNOWN, MODERATION_BLOCKED
    }

    /** 仅携带固定代码；供应商异常、参数和结果正文不进入异常信息。 */
    public static final class Failure extends RuntimeException {
        private final Code code;
        private final Dispatch dispatch;
        private final boolean dispatchEvidence;
        public Failure(Code code) { this(code, Dispatch.NOT_DISPATCHED, false); }
        public Failure(Code code, Dispatch dispatch) {
            this(code, dispatch, true);
        }
        private Failure(Code code, Dispatch dispatch, boolean dispatchEvidence) {
            super("AGENT_" + Objects.requireNonNull(code).name());
            this.code = code;
            this.dispatch = Objects.requireNonNull(dispatch);
            this.dispatchEvidence = dispatchEvidence;
        }
        public Code code() { return code; }
        public Dispatch dispatch() { return dispatch; }
        public boolean hasDispatchEvidence() { return dispatchEvidence; }
    }

    public record Context(String runId, Long tenantId, Long actorUserId, Long agentId,
            AgentEntryIdentity entry, String definitionHash, String ownerToken) {
        public Context {
            id(runId); Objects.requireNonNull(entry); hash(definitionHash);
            if (tenantId == null || tenantId <= 0 || actorUserId == null || actorUserId <= 0
                    || agentId == null || agentId <= 0) throw new Failure(Code.INVALID_REQUEST);
            if (entry instanceof AgentEntryIdentity.Chat) id(ownerToken);
            else if (ownerToken != null) throw new Failure(Code.INVALID_REQUEST);
        }
        public Context(String runId, Long tenantId, Long actorUserId, Long agentId, String executionId,
                String nodeRunId, int attemptNo, String workflowCode, String definitionHash) {
            this(runId, tenantId, actorUserId, agentId, new AgentEntryIdentity.Workflow(executionId,nodeRunId,attemptNo,workflowCode),definitionHash,null);
        }
        public String executionId() { return entry instanceof AgentEntryIdentity.Workflow w ? w.executionId() : null; }
        public String nodeRunId() { return entry instanceof AgentEntryIdentity.Workflow w ? w.nodeRunId() : null; }
        public Integer attemptNo() { return entry instanceof AgentEntryIdentity.Workflow w ? w.attemptNo() : null; }
        public String workflowCode() { return entry instanceof AgentEntryIdentity.Workflow w ? w.workflowCode() : null; }
        @Override public String toString() { return "AgentContext[runId=" + runId + ", entry=" + entry.getClass().getSimpleName() + "]"; }
    }

    public record ModelSelection(Long modelId, String definitionHash) {
        public ModelSelection {
            if (modelId == null || modelId <= 0) throw new Failure(Code.INVALID_REQUEST);
            hash(definitionHash);
        }
    }

    public record ToolSnapshot(Long toolId, long revision, long connectionRevision,
            long catalogRevision, String structureHash) {
        public ToolSnapshot {
            if (toolId == null || toolId <= 0 || revision < 1 || connectionRevision < 1
                    || catalogRevision < 1) throw new Failure(Code.INVALID_REQUEST);
            hash(structureHash);
        }
    }

    /** 动态参数仅允许原样 externalQuery；其他参数由管理员固定。 */
    public record ToolBinding(String alias, ToolSnapshot snapshot, String purpose,
            String inputSchemaJson, String queryArgument, String fixedArgumentsJson) {
        public ToolBinding {
            if (alias == null || !alias.matches("tool_[1-6]") || snapshot == null
                    || purpose == null || purpose.isBlank() || purpose.length() > 512
                    || inputSchemaJson == null || fixedArgumentsJson == null
                    || queryArgument != null && !queryArgument.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
                throw new Failure(Code.INVALID_REQUEST);
            }
        }
        @Override public String toString() { return "ToolBinding[alias=" + alias + ", toolId=" + snapshot.toolId() + "]"; }
    }

    /** 模型只能见别名、管理员用途与受限 Schema，看不到平台 ID、凭证或知识正文。 */
    public record ToolDefinition(String alias, String purpose, String schemaJson) {
        @Override public String toString() { return "ToolDefinition[alias=" + alias + "]"; }
    }

    public enum EvidencePolicy { QUERY_EVIDENCE, CONTINUE_HISTORY }
    public record HistoryTurn(Long userMessageId, Long assistantMessageId, String question, String answer) {
        public HistoryTurn {
            if (userMessageId == null || userMessageId <= 0 || assistantMessageId == null || assistantMessageId <= userMessageId
                    || question == null || question.isBlank() || answer == null || answer.isBlank()) throw new Failure(Code.INVALID_REQUEST);
        }
        @Override public String toString() { return "HistoryTurn[assistantMessageId=" + assistantMessageId + "]"; }
    }
    public record Request(Context context, ModelSelection model, String rolePrompt, String question,
            String externalQuery, boolean knowledgeRequired, List<ToolBinding> tools,
            int maxCalls, Duration budget, EvidencePolicy evidencePolicy, List<HistoryTurn> history) {
        public Request(Context context, ModelSelection model, String rolePrompt, String question, String externalQuery,
                boolean knowledgeRequired,List<ToolBinding> tools,int maxCalls,Duration budget) {
            this(context,model,rolePrompt,question,externalQuery,knowledgeRequired,tools,maxCalls,budget,EvidencePolicy.QUERY_EVIDENCE,List.of());
        }
        public Request {
            Objects.requireNonNull(context); Objects.requireNonNull(model);
            rolePrompt = rolePrompt == null ? "" : rolePrompt;
            if (question == null || question.isBlank() || question.length() > 4000
                    || rolePrompt.length() > 8000
                    || externalQuery == null || externalQuery.length() > 4000
                    || maxCalls < 0 || maxCalls > 2 || budget == null
                    || budget.toMillis() < 100 || budget.compareTo(Duration.ofSeconds(180)) > 0) {
                throw new Failure(Code.INVALID_REQUEST);
            }
            tools = tools == null ? List.of() : List.copyOf(tools);
            history = history == null ? List.of() : List.copyOf(history);
            if (evidencePolicy == null || history.size() > 6
                    || history.stream().mapToLong(t -> (long)t.question().length()+t.answer().length()).sum()>16000
                    || context.entry() instanceof AgentEntryIdentity.Workflow && (!history.isEmpty() || evidencePolicy != EvidencePolicy.QUERY_EVIDENCE)
                    || evidencePolicy == EvidencePolicy.CONTINUE_HISTORY && (history.isEmpty() || knowledgeRequired || !tools.isEmpty()))
                throw new Failure(Code.INVALID_REQUEST);
            if (tools.size() > 6 || tools.stream().map(ToolBinding::alias).distinct().count() != tools.size()
                    || tools.stream().map(tool -> tool.snapshot().toolId()).distinct().count() != tools.size()
                    || !tools.isEmpty() && (externalQuery.isBlank() || maxCalls == 0)) {
                throw new Failure(Code.INVALID_REQUEST);
            }
        }
        @Override public String toString() { return "AgentRequest[runId=" + context.runId() + ", maxCalls=" + maxCalls + "]"; }
    }

    public record Proposal(String callId, String alias, String argumentsJson) {
        @Override public String toString() { return "Proposal[content=<redacted>]"; }
    }
    public record Usage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        public Usage {
            if (inputTokens != null && inputTokens < 0 || outputTokens != null && outputTokens < 0
                    || totalTokens != null && totalTokens < 0) throw new Failure(Code.MODEL_OUTPUT_INVALID);
        }
        public static Usage unknown() { return new Usage(null, null, null); }
    }
    public record Plan(List<Proposal> calls, Usage usage) {
        public Plan {
            if (calls == null) throw new Failure(Code.MODEL_OUTPUT_INVALID);
            calls = List.copyOf(calls);
            usage = usage == null ? Usage.unknown() : usage;
        }
        @Override public String toString() { return "Plan[callCount=" + calls.size() + ", usage=" + usage + "]"; }
    }
    public record PlanningInput(ModelSelection model, String externalQuery, List<ToolDefinition> tools) {
        public PlanningInput { tools = List.copyOf(tools); }
        @Override public String toString() { return "PlanningInput[toolCount=" + tools.size() + "]"; }
    }

    /** 必须由已鉴权的知识桥接生成，正文仅用于最终回答。 */
    public record KnowledgeEvidence(String citationId, String content) {
        public KnowledgeEvidence {
            if (citationId == null || !citationId.matches("S[1-9][0-9]{0,2}") || content == null
                    || content.isBlank() || content.length() > 16000) throw new Failure(Code.KNOWLEDGE_UNAVAILABLE);
        }
        @Override public String toString() { return "KnowledgeEvidence[citationId=" + citationId + "]"; }
    }
    public record ToolIntent(Context context, int callNo, ToolSnapshot tool, String argumentsJson) {
        public ToolIntent {
            if (callNo < 1 || callNo > 2) throw new Failure(Code.INVALID_PLAN);
            Objects.requireNonNull(context); Objects.requireNonNull(tool); Objects.requireNonNull(argumentsJson);
        }
        @Override public String toString() { return "ToolIntent[runId=" + context.runId() + ", callNo=" + callNo + "]"; }
    }
    /** Gateway 只在审计终态完成后报告成功，UNKNOWN 永远不能用来回答。 */
    public record ToolResult(String invocationId, boolean succeeded, boolean auditRecorded,
            Dispatch dispatch, String content, boolean truncated) {
        public ToolResult {
            id(invocationId); Objects.requireNonNull(dispatch);
            if (content != null && content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256 * 1024) {
                throw new Failure(Code.BUDGET_EXCEEDED, dispatch);
            }
        }
        @Override public String toString() { return "ToolResult[invocationId=" + invocationId + ", succeeded=" + succeeded + "]"; }
    }
    public record ToolEvidence(String citationId, String invocationId, int callNo, String content, boolean truncated) {
        @Override public String toString() { return "ToolEvidence[citationId=" + citationId + ", invocationId=" + invocationId + "]"; }
    }
    public record AnswerInput(ModelSelection model, String rolePrompt, String question,
            List<KnowledgeEvidence> knowledge, List<ToolEvidence> tools, List<HistoryTurn> history, EvidencePolicy evidencePolicy) {
        public AnswerInput(ModelSelection model,String rolePrompt,String question,List<KnowledgeEvidence> knowledge,List<ToolEvidence> tools) {
            this(model,rolePrompt,question,knowledge,tools,List.of(),EvidencePolicy.QUERY_EVIDENCE);
        }
        public AnswerInput { knowledge = List.copyOf(knowledge); tools = List.copyOf(tools); history = List.copyOf(history); }
        @Override public String toString() { return "AnswerInput[knowledgeCount=" + knowledge.size() + ", toolCount=" + tools.size() + "]"; }
    }
    public record Answer(String text, Usage usage, List<Long> historyMessageIds) {
        public Answer(String text,Usage usage) { this(text,usage,List.of()); }
        public Answer {
            if (text == null || text.isBlank() || text.length() > 16000) throw new Failure(Code.MODEL_OUTPUT_INVALID);
            usage = usage == null ? Usage.unknown() : usage;
            historyMessageIds = List.copyOf(historyMessageIds);
        }
        @Override public String toString() { return "Answer[usage=" + usage + "]"; }
    }
    public record ToolSource(String citationId, String invocationId, int callNo, boolean truncated) { }
    public record Result(String text, List<String> knowledgeCitations, List<ToolSource> toolSources,
            Usage planningUsage, Usage answerUsage, List<Long> historyMessageIds) {
        public Result(String text,List<String> knowledgeCitations,List<ToolSource> toolSources,Usage planningUsage,Usage answerUsage) {
            this(text,knowledgeCitations,toolSources,planningUsage,answerUsage,List.of());
        }
        public Result { knowledgeCitations = List.copyOf(knowledgeCitations); toolSources = List.copyOf(toolSources); historyMessageIds=List.copyOf(historyMessageIds); }
        @Override public String toString() { return "AgentResult[knowledgeCount=" + knowledgeCitations.size() + ", toolCount=" + toolSources.size() + "]"; }
    }

    private static void id(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.:-]{1,64}")) throw new Failure(Code.INVALID_REQUEST);
    }
    private static void hash(String value) {
        if (value == null || !value.matches("[a-f0-9]{64}")) throw new Failure(Code.INVALID_REQUEST);
    }
}
