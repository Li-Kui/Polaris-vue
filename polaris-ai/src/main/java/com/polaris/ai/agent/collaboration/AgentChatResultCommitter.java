package com.polaris.ai.agent.collaboration;

import com.polaris.ai.dto.KnowledgeCitation;
import com.polaris.ai.knowledge.ChatKnowledgeEvidenceService;
import com.polaris.ai.mapper.AgentChatMapper;
import com.polaris.ai.mapper.AgentRunMapper;
import com.polaris.ai.safety.dto.ModerationRequest;
import com.polaris.ai.safety.dto.ModerationResult;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.service.IModerationFacade;

import java.util.*;
import java.util.regex.Pattern;

import static com.polaris.ai.agent.collaboration.AgentChatContracts.*;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 单运行提交器：先审核，随后在 Ledger 的同一短事务保存消息和成功状态。 */
public final class AgentChatResultCommitter implements ChatAgentRunLedger.Completion {
    private final Permit permit;
    private final HistorySnapshot history;
    private final AgentChatMapper chats;
    private final AgentRunMapper runs;
    private final AgentChatSourceGuard sources;
    private final ChatAgentKnowledgeGateway knowledge;
    private final IModerationFacade moderation;
    private final ModerationResult approvedInput;
    private ModerationResult approvedOutput;
    private Dependencies dependencies;
    private String citationArchive;
    private Long messageId;
    public AgentChatResultCommitter(Permit permit,HistorySnapshot history,AgentChatMapper chats,AgentRunMapper runs,
            AgentChatSourceGuard sources,ChatAgentKnowledgeGateway knowledge,IModerationFacade moderation,ModerationResult input) {
        this.permit=permit;this.history=history;this.chats=chats;this.runs=runs;this.sources=sources;this.knowledge=knowledge;
        this.moderation=moderation;this.approvedInput=input;
    }
    public Long messageId() {return messageId;}
    public Dependencies dependencies() {return dependencies;}
    @Override public void prepare(Result result) {
        approvedOutput=moderation.moderate(ModerationRequest.of(ModerationScene.AI_OUTPUT,result.text(),"AI_CONVERSATION",permit.scope().conversationId().toString()));
        if(approvedOutput==null || !approvedOutput.isAllowed() || approvedInput==null || !approvedInput.isAllowed())throw new Failure(Code.MODERATION_BLOCKED);
        if(!knowledge.citations().stream().map(KnowledgeCitation::citationId).toList().equals(result.knowledgeCitations()))throw new Failure(Code.MODEL_OUTPUT_INVALID);
        citationArchive=ChatKnowledgeEvidenceService.encode(knowledge.citations(),AgentPlanValidator.JSON);
        Set<KnowledgeDependency> kb=new LinkedHashSet<>();Set<ToolDependency> tools=new LinkedHashSet<>();Set<HistoricalReference> allowed=new HashSet<>();
        var ids=result.historyMessageIds();List<Long> expected=new ArrayList<>();
        for(var turn:history.turns()) if(ids.contains(turn.assistantMessageId()) || ids.contains(turn.userMessageId())) {
            expected.add(turn.userMessageId());expected.add(turn.assistantMessageId());
            kb.addAll(turn.dependencies().knowledge());tools.addAll(turn.dependencies().tools());allowed.addAll(turn.dependencies().history());
            var original=chats.message(permit.scope(),turn.assistantMessageId());if(original==null)throw new Failure(Code.SNAPSHOT_CHANGED);
            String archive=chats.citations(permit.scope(),turn.assistantMessageId());
            if(archive!=null && !archive.isBlank()) {
                var decoded=AgentPlanValidator.JSON.readValue(archive,ChatKnowledgeEvidenceService.Archive.class);
                if(decoded==null || decoded.version()!=1 || decoded.citations()==null || decoded.citations().size()>50)throw new Failure(Code.SNAPSHOT_CHANGED);
                decoded.citations().forEach(ref->allowed.add(new HistoricalReference(turn.assistantMessageId(),ref.citationId())));
            }
            turn.dependencies().tools().stream().filter(t->t.runId().equals(original.getAgentRunId())).forEach(t->allowed.add(new HistoricalReference(turn.assistantMessageId(),"T"+t.callNo())));
        }
        if(!expected.equals(ids))throw new Failure(Code.MODEL_OUTPUT_INVALID);
        knowledge.citations().forEach(ref->kb.add(new KnowledgeDependency(ref.knowledgeBaseId(),ref.documentId(),ref.indexVersion(),ref.collectionHash())));
        result.toolSources().forEach(ref->tools.add(new ToolDependency(ref.invocationId(),permit.runId(),ref.callNo())));
        Set<HistoricalReference> referenced=new LinkedHashSet<>();
        var matcher=Pattern.compile("\\[H([1-9][0-9]{0,18}):(S[1-9][0-9]{0,2}|T[12])\\]").matcher(result.text());
        while(matcher.find()) {
            var ref=new HistoricalReference(Long.valueOf(matcher.group(1)),matcher.group(2));
            if(!allowed.contains(ref))throw new Failure(Code.MODEL_OUTPUT_INVALID);referenced.add(ref);
        }
        dependencies=new Dependencies(1,List.copyOf(kb),List.copyOf(tools),List.copyOf(referenced));
    }
    @Override public void commit(AgentChatRequestRow row,Result result) {
        knowledge.checkCurrent();
        // 本轮工具审计已经成功，run 尚未终结；历史工具在来源守卫中按其真实原运行核对。
        if(!sources.current(permit.scope(),dependencies))throw new Failure(Code.SNAPSHOT_CHANGED);
        var message=new AgentChatHistoryRow();message.setConversationId(permit.scope().conversationId());message.setContent(result.text());
        message.setAgentRunId(permit.runId());message.setChatContextKey(row.getContextKey());message.setHistoryDependencies(AgentPlanValidator.JSON.writeValueAsString(dependencies));
        try {
            Integer tokens=result.answerUsage().totalTokens();
            if(chats.approveUser(permit.scope(),permit.runId(),approvedInput.dictionaryVersion())!=1
                    || chats.insertAnswer(message,citationArchive,approvedOutput.dictionaryVersion(),tokens)!=1 || message.getId()==null)
                throw new Failure(Code.AUDIT_INCOMPLETE);
        } catch(Failure safe) {throw safe;}catch(RuntimeException unavailable) {throw new Failure(Code.AUDIT_INCOMPLETE);}
        messageId=message.getId();row.setAssistantMessageId(messageId);
    }
}
