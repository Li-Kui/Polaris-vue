package com.polaris.ai.agent.collaboration;

import com.polaris.ai.mapper.AgentChatMapper;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.polaris.ai.agent.collaboration.AgentChatContracts.*;

/** 保留完整已完成轮次；历史仅交给回答/知识适配器，绝不进入工具规划输入。 */
@Service
public class AgentChatHistoryService {
    private final AgentChatMapper chats;
    private final AgentChatSourceGuard sources;
    public AgentChatHistoryService(AgentChatMapper chats, AgentChatSourceGuard sources) { this.chats = chats; this.sources = sources; }

    public History build(Scope scope, String contextKey, long start, boolean inherit) {
        hash(contextKey);
        var rows = new ArrayList<>(chats.history(scope));
        rows.sort(Comparator.comparing(AgentChatHistoryRow::getId));
        long watermark = rows.stream().mapToLong(AgentChatHistoryRow::getId).max().orElse(0);
        List<AgentChatHistoryRow[]> candidates = new ArrayList<>();
        Map<Long, Dependencies> dependencies = new HashMap<>(); int excluded = 0;
        for (int i = 0; i < rows.size(); i++) {
            var user = rows.get(i);
            if (!"user".equals(user.getRole()) || user.getId() < start) continue;
            if (i + 1 >= rows.size()) { excluded++; continue; }
            var answer = rows.get(i + 1);
            if (!"assistant".equals(answer.getRole()) || user.getAgentRunId() == null
                    || !user.getAgentRunId().equals(answer.getAgentRunId())) { excluded++; continue; }
            i++;
            if (!eligible(user, scope, contextKey, inherit) || !eligible(answer, scope, contextKey, inherit)) { excluded++; continue; }
            Dependencies refs;
            try {
                if (answer.getHistoryDependencies() == null || answer.getHistoryDependencies().length() > 50000) throw invalid();
                refs = AgentPlanValidator.JSON.readValue(answer.getHistoryDependencies(), Dependencies.class);
                if (refs == null) throw invalid();
            } catch (RuntimeException invalidArchive) { excluded++; continue; }
            // 数据库故障向上传播，不把无法复查解释为可信空来源。
            if (!sources.current(scope, refs)) { excluded++; continue; }
            candidates.add(new AgentChatHistoryRow[] { user, answer }); dependencies.put(answer.getId(), refs);
        }
        int chars = 0; List<AgentChatHistoryRow[]> selected = new ArrayList<>(); boolean truncated = rows.size() == 100;
        for (int i = candidates.size() - 1; i >= 0; i--) {
            var pair = candidates.get(i); int length = pair[0].getContent().length() + pair[1].getContent().length();
            if (selected.size() == MAX_TURNS || chars + length > MAX_HISTORY_CHARS) { truncated = true; break; }
            chars += length; selected.add(pair);
        }
        Collections.reverse(selected);
        List<HistoryMessage> messages = new ArrayList<>(); List<TurnRef> refs = new ArrayList<>();
        for (var pair : selected) {
            var user = pair[0]; var answer = pair[1]; var deps = dependencies.get(answer.getId());
            messages.add(new HistoryMessage(user.getId(), "user", user.getContent()));
            messages.add(new HistoryMessage(answer.getId(), "assistant", answer.getContent()));
            refs.add(new TurnRef(user.getId(), answer.getId(), digest(user.getContent()), digest(answer.getContent()), digest(deps), deps));
        }
        StringBuilder retrieval = new StringBuilder();
        for (int i = Math.max(0, selected.size() - 2); i < selected.size(); i++) {
            String text = selected.get(i)[0].getContent();
            int length = Math.min(1000, text.length());
            if (length < text.length() && Character.isHighSurrogate(text.charAt(length - 1))) length--;
            if (!retrieval.isEmpty()) retrieval.append('\n');
            retrieval.append(text, 0, length);
        }
        return new History(new HistorySnapshot(watermark, start, refs, excluded, truncated), messages, retrieval.toString());
    }
    private boolean eligible(AgentChatHistoryRow row, Scope scope, String key, boolean inherit) {
        return Objects.equals(row.getConversationId(), scope.conversationId()) && "SUCCEEDED".equals(row.getGenerationStatus())
                && "SAFE".equals(row.getModerationStatus()) && !Boolean.TRUE.equals(row.getHasAttachments())
                && row.getContent() != null && !row.getContent().isBlank()
                && (inherit || key.equals(row.getChatContextKey()));
    }

    /** 新回答保守继承所用轮次全部来源；扁平去重并设上限，不递归执行消息图。 */
    public Dependencies inherited(HistorySnapshot snapshot) {
        Set<KnowledgeDependency> knowledge = new LinkedHashSet<>(); Set<ToolDependency> tools = new LinkedHashSet<>();
        Set<HistoricalReference> historical=new LinkedHashSet<>();
        snapshot.turns().forEach(turn -> { knowledge.addAll(turn.dependencies().knowledge()); tools.addAll(turn.dependencies().tools());historical.addAll(turn.dependencies().history()); });
        return new Dependencies(1, List.copyOf(knowledge), List.copyOf(tools),List.copyOf(historical));
    }

    /** 领取后按冻结ID读取，不把本轮新增用户消息或其他晚到消息加入上下文。 */
    public List<BoundedAgentContracts.HistoryTurn> restore(Scope scope,HistorySnapshot snapshot) {
        List<BoundedAgentContracts.HistoryTurn> result=new ArrayList<>();
        for(var ref:snapshot.turns()) {
            var user=chats.message(scope,ref.userMessageId());var answer=chats.message(scope,ref.assistantMessageId());
            if(user==null || answer==null || !"user".equals(user.getRole()) || !"assistant".equals(answer.getRole())
                    || user.getAgentRunId()==null || !user.getAgentRunId().equals(answer.getAgentRunId())
                    || !eligible(user,scope,"",true) || !eligible(answer,scope,"",true)
                    || !digest(user.getContent()).equals(ref.userHash()) || !digest(answer.getContent()).equals(ref.assistantHash())
                    || !sources.current(scope,ref.dependencies())) throw new BoundedAgentContracts.Failure(BoundedAgentContracts.Code.SNAPSHOT_CHANGED);
            Dependencies current;
            try {current=AgentPlanValidator.JSON.readValue(answer.getHistoryDependencies(),Dependencies.class);}
            catch(RuntimeException invalid) {throw new BoundedAgentContracts.Failure(BoundedAgentContracts.Code.SNAPSHOT_CHANGED);}
            if(!digest(current).equals(ref.dependenciesHash()))throw new BoundedAgentContracts.Failure(BoundedAgentContracts.Code.SNAPSHOT_CHANGED);
            result.add(new BoundedAgentContracts.HistoryTurn(user.getId(),answer.getId(),user.getContent(),answer.getContent()));
        }
        return List.copyOf(result);
    }
}
