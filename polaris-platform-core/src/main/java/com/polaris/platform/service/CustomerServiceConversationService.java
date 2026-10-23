package com.polaris.platform.service;

import com.polaris.ai.customer.application.CustomerServiceAiGateway;
import com.polaris.ai.dto.KnowledgeCitation;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.CustomerServiceRelease;
import com.polaris.platform.domain.CustomerServiceSession;
import com.polaris.platform.domain.CustomerServiceTurn;
import com.polaris.platform.mapper.CustomerServiceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;

/** 数据库串行化会话变更；事务外调用 AI，完成时再次验证持久化轮次围栏。 */
@Service
public class CustomerServiceConversationService {
    private static final long SESSION_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final long TURN_MILLIS = Duration.ofMinutes(3).toMillis();
    private static final Set<String> SAFE_ANSWER_ERRORS = Set.of("CONTENT_BLOCKED", "KNOWLEDGE_SEARCH_FAILED",
            "MODEL_CALL_FAILED", "EVIDENCE_NOT_READY", "EVIDENCE_UNAVAILABLE", "RESOURCE_UNAVAILABLE", "RESPONSE_MODE_UNAVAILABLE", "ANSWER_INVALID", "ROUTE_INVALID");
    private final CustomerServiceMapper mapper;
    private final CustomerServiceAccess access;
    private final CustomerServiceConfigurationService configurations;
    private final CustomerServiceAiGateway ai;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final TokenQuotaService quotas;
    private final CustomerServiceIntentService intents;
    private final CustomerServiceChannelService channels;

    public CustomerServiceConversationService(CustomerServiceMapper mapper, CustomerServiceAccess access,
            CustomerServiceConfigurationService configurations, CustomerServiceAiGateway ai, ObjectMapper json,
            PlatformTransactionManager transactionManager, TokenQuotaService quotas, CustomerServiceIntentService intents,
            CustomerServiceChannelService channels) {
        this.mapper = mapper;
        this.access = access;
        this.configurations = configurations;
        this.ai = ai;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
        this.quotas = quotas;
        this.intents = intents;
        this.channels = channels;
    }

    public SessionView create(Long appId) { return create(appId, null); }

    public SessionView createFromChannel(String code) {
        return create(channels.requireEntry(code).getAppId(), code);
    }

    public SessionView createVisitor(String code) {
        if (!(com.polaris.ai.core.context.CallerContextHolder.get() instanceof com.polaris.ai.core.context.CustomerServiceVisitorContext)) throw missing();
        return create(channels.requireVisitorEntry(code).getAppId(), code);
    }

    private SessionView create(Long appId, String channelCode) {
        var actor = access.requireConversationActor();
        var visitor = visitor();
        if (visitor != null && channelCode == null) throw missing();
        String id = transactions.execute(status -> {
            var app = mapper.lockApp(actor.tenantId(), appId);
            if (app == null || !"ACTIVE".equals(app.getStatus()) || app.getCurrentReleaseId() == null) {
                throw new ServiceException("客服未发布、已停用或无权访问", 404);
            }
            if (visitor == null && mapper.countOpenSessions(actor.tenantId(), actor.userId(), appId, new Date()) >= 5) {
                throw new ServiceException("RATE_LIMITED: 请先关闭已有测试会话", 429);
            }
            var channel = channelCode == null ? null : visitor == null ? channels.requireEntry(channelCode) : channels.requireVisitorEntry(channelCode);
            if (channel != null && !appId.equals(channel.getAppId())) throw missing();
            var release = requireRelease(actor.tenantId(), channel == null ? app.getCurrentReleaseId() : channel.getReleaseId(), appId);
            ai.requireAvailable(configurations.config(release.getConfigJson()), release.getModelHash());
            var session = new CustomerServiceSession();
            session.setId(visitor == null ? UUID.randomUUID().toString() : visitor.sessionId());
            session.setVisitorTokenHash(visitor == null ? null : visitor.tokenHash());
            session.setTenantId(actor.tenantId());
            session.setUserId(actor.userId());
            if (channel != null) {
                session.setChannelId(channel.getId());
                session.setChannelRevision(channel.getRevision());
            }
            session.setRuntimeEpoch(app.getRuntimeEpoch());
            session.setAppId(appId);
            session.setReleaseId(release.getId());
            session.setCreateTime(new Date());
            session.setExpiresAt(new Date(session.getCreateTime().getTime() + SESSION_MILLIS));
            if (mapper.insertSession(session) != 1) throw new ServiceException("创建客服会话失败");
            return session.getId();
        });
        return get(id);
    }

    public SessionView get(String sessionId) {
        var actor = access.requireConversationActor();
        return transactions.execute(status -> {
            var session = requireSession(actor, sessionId, false);
            var release = requireRelease(actor.tenantId(), session.getReleaseId(), session.getAppId());
            // 撤权后不通过旧会话返回知识正文。停用仍允许所有者查看已完成的非媒体结果。
            var config = configurations.config(release.getConfigJson());
            ai.requireAvailable(config, release.getModelHash());
            mapper.expireTurns(actor.tenantId(), sessionId, new Date());
            var app = configurations.requireApp(actor.tenantId(), session.getAppId());
            String state = session.getStatus();
            if (!Objects.equals(app.getRuntimeEpoch(), session.getRuntimeEpoch())) {
                mapper.interruptTurns(actor.tenantId(), sessionId, null, new Date());
                state = "CLOSED";
            } else if (!session.getExpiresAt().after(new Date())) state = "EXPIRED";
            return new SessionView(session.getId(), session.getAppId(), session.getReleaseId(), state,
                    session.getExpiresAt(), config.welcomeMessage(),
                    mapper.selectTurns(actor.tenantId(), sessionId).stream().map(this::view).toList(),
                    config.avatar(), config.contact());
        });
    }

    public TurnView send(String sessionId, String requestId, String question) {
        return send(sessionId, requestId, question, null);
    }

    /** 同一请求只生成一次；断线后通过会话核对最终状态，不重新生成或重播句段。 */
    public TurnView sendStream(String sessionId, String requestId, String question, java.util.function.Consumer<AnswerEvent> sink) {
        return send(sessionId, requestId, question, Objects.requireNonNull(sink));
    }

    private TurnView send(String sessionId, String requestId, String question, java.util.function.Consumer<AnswerEvent> sink) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,64}")
                || question == null || question.isBlank() || question.length() > 2000) {
            throw new ServiceException("INVALID_INPUT: 请求标识无效或问题超过 2000 字", 400);
        }
        var actor = access.requireConversationActor();
        String input = question.strip();
        var start = transactions.execute(status -> begin(actor, sessionId, requestId, input));
        if (!start.created()) return view(start.turn());
        var turn = start.turn();
        CustomerServiceAiGateway.Answer result = null;
        String error = null;
        TokenQuotaService.TokenReservation reservation = null;
        var measuredUsage = new Usage();
        CustomerServiceIntentService.Intent intent = null;
        var parts = new ArrayList<CustomerServiceAiGateway.AnswerPart>();
        try {
            if (sink != null) sink.accept(new AnswerEvent("accepted", turn.getId(), requestId, null, null, null));
            // 预览也走既有配额准入；估算预占不是供应商费用硬预算。
            var release = start.release();
            var config = configurations.config(release.getConfigJson());
            var options = visitor() != null || config.capabilities().isEmpty() ? List.<CustomerServiceAiGateway.RouteCapability>of() : intents.options(sessionId);
            reservation = quotas.reserve(actor.tenantId().toString(), options.isEmpty() ? 30_000 : 60_000);
            if (!options.isEmpty()) {
                var pending = intents.pending(sessionId, mapper.selectTurns(actor.tenantId(), sessionId), turn.getTurnNo(), options);
                var decision = ai.route(config, release.getModelHash(), options, pending, input, () -> active(actor, turn), measuredUsage::add);
                intent = intents.decide(sessionId, decision, options, pending);
                if (!"KNOWLEDGE".equals(intent.state())) {
                    result = new CustomerServiceAiGateway.Answer(intents.answer(sessionId, intent, options), List.of(), NormalizedUsage.unknown());
                }
            }
            List<CustomerServiceAiGateway.History> history = new ArrayList<>();
            for (var previous : mapper.selectTurns(actor.tenantId(), sessionId)) {
                if (previous.getTurnNo() >= turn.getTurnNo() || !"COMPLETED".equals(previous.getStatus())) continue;
                if (previous.getIntentJson() != null && !intents.knowledge(previous)) continue;
                var visible = view(previous);
                if (visible.answer() != null) history.add(new CustomerServiceAiGateway.History(visible.question(), visible.answer()));
            }
            if (result == null) {
                if (sink == null) result = ai.answer(config, release.getModelHash(), history,
                        input, () -> active(actor, turn), measuredUsage::add);
                else result = ai.answerStream(config, release.getModelHash(), history, input, () -> active(actor, turn),
                        measuredUsage::add, part -> {
                            if (part.citations().isEmpty() || part.citations().size() > 12 || part.text() == null || part.text().isBlank() || part.text().length() > 600 || parts.size() >= 8
                                    || parts.stream().mapToInt(value -> value.text().length()).sum() + part.text().length() > 4000) {
                                throw new ServiceException("ANSWER_INVALID: 回答分段超出限制", 502);
                            }
                            transactions.executeWithoutResult(status -> {
                                requireSession(actor, sessionId, true);
                                ai.requireAvailable(config, release.getModelHash()); ai.sources(part.citations());
                                if (!active(actor, turn)) throw missing();
                                parts.add(part); turn.setAnswerPartsJson(json.writeValueAsString(parts)); turn.setFinishedTime(new Date());
                                if (mapper.saveAnswerParts(turn) != 1) throw missing();
                            });
                            sink.accept(new AnswerEvent("part", turn.getId(), requestId, parts.size(), part.text(), null));
                        });
            }
        } catch (RuntimeException failure) {
            // 不保存或对外返回供应商异常、端点、凭据及输入副本。
            if (failure instanceof TokenQuotaService.TokenQuotaExceededException) error = "QUOTA_EXCEEDED";
            else if (failure instanceof TokenQuotaService.TokenQuotaUnavailableException) error = "QUOTA_UNAVAILABLE";
            else error = answerErrorCode(failure);
        } finally {
            if (reservation != null) {
                try {
                    Integer tokens = measuredUsage.knownTokens;
                    if (tokens == null) quotas.release(reservation);
                    else quotas.complete(reservation, tokens);
                } catch (RuntimeException settlementFailure) {
                    error = "USAGE_SETTLEMENT_FAILED";
                }
            }
        }
        if (result != null && !parts.isEmpty() && !result.text().startsWith(parts.stream().map(CustomerServiceAiGateway.AnswerPart::text).collect(java.util.stream.Collectors.joining()))) {
            result = null; error = "ANSWER_INVALID";
        }
        var completed = result;
        var errorCode = error;
        var checkedIntent = intent;
        transactions.executeWithoutResult(status -> {
            requireSession(actor, sessionId, false);
            mapper.expireTurns(actor.tenantId(), sessionId, new Date());
            if (!active(actor, turn)) {
                mapper.interruptTurns(actor.tenantId(), sessionId, turn.getId(), new Date());
                return;
            }
            if (completed != null && errorCode == null) {
                try {
                    if (checkedIntent != null) turn.setIntentJson(json.writeValueAsString(intents.revalidate(sessionId, checkedIntent)));
                } catch (RuntimeException unavailable) {
                    turn.setStatus("FAILED");
                    turn.setErrorCode("CAPABILITY_UNAVAILABLE");
                    turn.setFinishedTime(new Date());
                    applyUsage(turn, measuredUsage.value);
                    mapper.finishTurn(turn);
                    return;
                }
                turn.setAnswer(completed.text());
                turn.setCitationsJson(json.writeValueAsString(completed.citations()));
                turn.setStatus("COMPLETED");
                turn.setErrorCode(errorCode);
            } else {
                turn.setStatus("FAILED");
                turn.setErrorCode(errorCode);
            }
            applyUsage(turn, measuredUsage.value);
            turn.setFinishedTime(new Date());
            mapper.finishTurn(turn);
        });
        return transactions.execute(status -> {
            requireSession(actor, sessionId, false);
            return view(mapper.selectRequest(actor.tenantId(), sessionId, requestId));
        });
    }

    /** 只读取最新轮次已校验并持久化的分段，不从客户端接收待播报文字。 */
    public String answerPart(String sessionId, String turnId, Integer partNo) {
        if (partNo == null || partNo < 1 || partNo > 8) throw missing();
        var actor = access.requireConversationActor();
        return transactions.execute(status -> {
            var session = requireSession(actor, sessionId, true);
            var release = requireRelease(actor.tenantId(), session.getReleaseId(), session.getAppId());
            ai.requireAvailable(configurations.config(release.getConfigJson()), release.getModelHash());
            var turns = mapper.selectTurns(actor.tenantId(), sessionId);
            if (turns.isEmpty()) throw missing();
            var turn = turns.get(turns.size() - 1);
            if (!Objects.equals(turnId, turn.getId()) || turn.getErrorCode() != null || turn.getAnswerPartsJson() == null
                    || !("COMPLETED".equals(turn.getStatus()) || "RUNNING".equals(turn.getStatus()) && active(actor, turn))) throw missing();
            var parts = json.readValue(turn.getAnswerPartsJson(), CustomerServiceAiGateway.AnswerPart[].class);
            if (parts.length > 8 || parts.length < partNo) throw missing();
            var part = parts[partNo - 1];
            ai.sources(part.citations());
            if (part.text() == null || part.text().isBlank() || part.text().length() > 600) throw missing();
            return part.text();
        });
    }

    public record AnswerEvent(String type, String turnId, String requestId, Integer partNo, String text, TurnView turn) { }

    /** 历史记录只读投影；不恢复会话、不清算超时，不绕过当前来源及渠道授权。 */
    public List<TurnView> recordContent(String sessionId) {
        var actor = access.requireAdmin();
        var session = mapper.selectSession(actor.tenantId(), actor.userId(), sessionId);
        if (session == null) throw missing();
        channels.requireSession(session);
        var release = requireRelease(actor.tenantId(), session.getReleaseId(), session.getAppId());
        var config = configurations.config(release.getConfigJson());
        ai.requireAvailable(config, release.getModelHash());
        var turns = mapper.selectTurns(actor.tenantId(), sessionId).stream().map(this::recordView).toList();
        // 所有来源读取结束后再次复查，撤权后不返回已准备好的正文。
        channels.requireSession(session);
        ai.requireAvailable(config, release.getModelHash());
        if (!actor.equals(access.requireConversationActor())) throw missing();
        return turns;
    }

    public List<TurnView> visitorRecordContent(String sessionId) {
        var actor = access.requireAdmin();
        var session = mapper.selectSession(actor.tenantId(), 0L, sessionId);
        if (session == null || session.getVisitorTokenHash() == null) throw missing();
        channels.requireVisitorRecord(session);
        var release = requireRelease(actor.tenantId(), session.getReleaseId(), session.getAppId());
        var config = configurations.config(release.getConfigJson());
        ai.requireAvailable(config, release.getModelHash());
        var content = mapper.selectTurns(actor.tenantId(), sessionId).stream().map(this::recordView).toList();
        channels.requireVisitorRecord(session);
        ai.requireAvailable(config, release.getModelHash());
        access.requireAdmin();
        return content;
    }

    private com.polaris.ai.core.context.CustomerServiceVisitorContext visitor() {
        return com.polaris.ai.core.context.CallerContextHolder.get() instanceof com.polaris.ai.core.context.CustomerServiceVisitorContext value ? value : null;
    }

    private TurnView recordView(CustomerServiceTurn turn) {
        if (turn.getIntentJson() != null && !intents.knowledge(turn)) {
            // 历史页不通过运行态参数投影去读取工作流原始结果，也不恢复已失效的能力摘要。
            return new TurnView(turn.getId(), turn.getRequestId(), turn.getTurnNo(), turn.getQuestion(), null,
                    turn.getStatus(), "HISTORY_CAPABILITY_SUMMARY_HIDDEN", List.of(), null);
        }
        return view(turn);
    }

    /** 显式运行服务端已核验的最新参数摘要，不接受客户端回传能力或参数。 */
    public CustomerServiceOperationService.StartView run(String sessionId, String requestId) {
        var actor = access.requireAdmin();
        try {
            return transactions.execute(status -> {
                var session = requireSession(actor, sessionId, true);
                mapper.expireTurns(actor.tenantId(), sessionId, new Date());
                var turn = mapper.selectRequest(actor.tenantId(), sessionId, requestId);
                if (turn == null || !"COMPLETED".equals(turn.getStatus()) || turn.getIntentJson() == null) throw missing();
                // 已受理请求可核对原任务；尚未运行的旧摘要不能越过后续用户输入再启动。
                if (!Objects.equals(turn.getTurnNo(), session.getTurnCount()) && !intents.started(sessionId, turn)) {
                    throw new ServiceException("INTENT_CHANGED: 请使用最新一轮的参数摘要", 409);
                }
                var result = intents.start(sessionId, turn);
                // 明确拒绝需退出最外层 REQUIRED 事务后返回，不能提交已被内层标记回滚的事务。
                if ("REJECTED".equals(result.state())) throw new ServiceException("CUSTOMER_INTENT_START_REJECTED", 409);
                return result;
            });
        } catch (ServiceException rejected) {
            if ("CUSTOMER_INTENT_START_REJECTED".equals(rejected.getMessage())) {
                return new CustomerServiceOperationService.StartView("REJECTED", List.of(), null, "WORKFLOW_START_REJECTED");
            }
            throw rejected;
        }
    }

    public void interrupt(String sessionId, String turnId) {
        var actor = access.requireConversationActor();
        if (turnId == null || !turnId.matches("[a-f0-9-]{36}")) throw new ServiceException("INVALID_INPUT", 400);
        transactions.executeWithoutResult(status -> {
            requireSession(actor, sessionId, false, false);
            mapper.interruptTurns(actor.tenantId(), sessionId, turnId, new Date());
        });
    }

    public void close(String sessionId) {
        var actor = access.requireConversationActor();
        transactions.executeWithoutResult(status -> {
            requireSession(actor, sessionId, false, false);
            mapper.closeSession(actor.tenantId(), actor.userId(), sessionId);
            mapper.interruptTurns(actor.tenantId(), sessionId, null, new Date());
        });
    }

    public CustomerServiceAiGateway.Source source(String sessionId, String requestId, String citationId) {
        var actor = access.requireConversationActor();
        return transactions.execute(status -> {
            var session = requireSession(actor, sessionId, false);
            var release = requireRelease(actor.tenantId(), session.getReleaseId(), session.getAppId());
            ai.requireAvailable(configurations.config(release.getConfigJson()), release.getModelHash());
            var turn = mapper.selectRequest(actor.tenantId(), sessionId, requestId);
            if (turn == null || !"COMPLETED".equals(turn.getStatus())) throw missing();
            var ref = citations(turn).stream().filter(c -> c.citationId().equals(citationId)).findFirst().orElseThrow(this::missing);
            return ai.preview(ref);
        });
    }

    private Start begin(CustomerServiceAccess.Actor actor, String sessionId, String requestId, String input) {
        var session = requireSession(actor, sessionId, true);
        var release = requireRelease(actor.tenantId(), session.getReleaseId(), session.getAppId());
        ai.requireAvailable(configurations.config(release.getConfigJson()), release.getModelHash());
        mapper.expireTurns(actor.tenantId(), sessionId, new Date());
        var existing = mapper.selectRequest(actor.tenantId(), sessionId, requestId);
        if (existing != null) {
            if (!input.equals(existing.getQuestion())) throw new ServiceException("INVALID_INPUT: 同一请求标识不能更换问题", 409);
            return new Start(existing, release, false);
        }
        if (mapper.countActiveSpeech(actor.tenantId(), sessionId, new Date()) > 0) throw new ServiceException("SPEECH_BUSY: 请先完成或取消语音识别", 409);
        if (mapper.selectTurns(actor.tenantId(), sessionId).stream().anyMatch(t -> "RUNNING".equals(t.getStatus()))) {
            throw new ServiceException("TURN_BUSY: 本轮正在回答，请稍后重试", 409);
        }
        if (session.getTurnCount() >= (visitor() == null ? 50 : visitor().messageLimit())) throw new ServiceException("RATE_LIMITED: 测试会话已达 50 轮，请重新开始", 429);
        var turn = new CustomerServiceTurn();
        turn.setId(UUID.randomUUID().toString());
        turn.setTenantId(actor.tenantId());
        turn.setSessionId(sessionId);
        turn.setRequestId(requestId);
        turn.setTurnNo(session.getTurnCount() + 1);
        turn.setQuestion(input);
        turn.setStatus("RUNNING");
        turn.setCreateTime(new Date());
        turn.setDeadline(new Date(turn.getCreateTime().getTime() + TURN_MILLIS));
        if (mapper.advanceSession(actor.tenantId(), actor.userId(), sessionId, session.getTurnCount()) != 1
                || mapper.insertTurn(turn) != 1) throw new ServiceException("VERSION_CONFLICT: 会话已变化", 409);
        return new Start(turn, release, true);
    }

    /** 媒体只允许当前有效会话读取其固定发布引用，渠道围栏与普通调用保持一致。 */
    public com.polaris.ai.customer.application.CustomerServiceAvatarConfig avatar(String sessionId) {
        var actor = access.requireConversationActor();
        return transactions.execute(status -> {
            var session = requireSession(actor, sessionId, true);
            return configurations.config(requireRelease(actor.tenantId(), session.getReleaseId(), session.getAppId()).getConfigJson()).avatar();
        });
    }

    private CustomerServiceSession requireSession(CustomerServiceAccess.Actor actor, String id, boolean active) {
        return requireSession(actor, id, active, true);
    }

    private CustomerServiceSession requireSession(CustomerServiceAccess.Actor actor, String id, boolean active, boolean channelAccess) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw missing();
        var session = mapper.lockSession(actor.tenantId(), actor.userId(), id);
        if (session == null || !actor.tenantId().equals(session.getTenantId()) || !actor.userId().equals(session.getUserId())) throw missing();
        if (visitor() != null && (!Objects.equals(visitor().sessionId(), id)
                || !Objects.equals(visitor().channelId(), session.getChannelId())
                || !Objects.equals(visitor().tokenHash(), session.getVisitorTokenHash()))) throw missing();
        if (channelAccess) channels.requireSession(session);
        if (active) {
            var app = configurations.requireApp(actor.tenantId(), session.getAppId());
            if (!"ACTIVE".equals(session.getStatus()) || !"ACTIVE".equals(app.getStatus())
                    || !Objects.equals(app.getRuntimeEpoch(), session.getRuntimeEpoch())
                    || !session.getExpiresAt().after(new Date())) throw new ServiceException("SESSION_EXPIRED: 会话已关闭、过期或客服已停用", 409);
        }
        return session;
    }

    private CustomerServiceRelease requireRelease(Long tenantId, String id, Long appId) {
        var release = mapper.selectRelease(tenantId, id);
        if (release == null || !Objects.equals(appId, release.getAppId())) throw missing();
        return release;
    }

    private boolean active(CustomerServiceAccess.Actor actor, CustomerServiceTurn turn) {
        var current = access.requireConversationActor();
        return actor.equals(current) && mapper.countActive(actor.tenantId(), actor.userId(), turn.getSessionId(), turn.getId(), new Date()) == 1;
    }

    private List<KnowledgeCitation> citations(CustomerServiceTurn turn) {
        if (turn.getCitationsJson() == null) return List.of();
        var values = json.readValue(turn.getCitationsJson(), KnowledgeCitation[].class);
        if (values.length > 12) throw new ServiceException("来源数据无效", 409);
        return List.of(values);
    }

    private TurnView view(CustomerServiceTurn turn) {
        if (turn == null) throw missing();
        String answer = turn.getAnswer();
        List<CustomerServiceAiGateway.SourceLabel> sources = List.of();
        String error = turn.getErrorCode();
        CustomerServiceIntentService.IntentView intent = null;
        if (answer != null) {
            try {
                sources = ai.sources(citations(turn));
            } catch (RuntimeException unavailable) {
                answer = null;
                error = "SOURCE_UNAVAILABLE";
            }
        }
        if (turn.getIntentJson() != null) {
            try { intent = intents.view(turn.getSessionId(), turn); }
            catch (RuntimeException unavailable) { answer = null; error = "CAPABILITY_UNAVAILABLE"; }
        }
        return new TurnView(turn.getId(), turn.getRequestId(), turn.getTurnNo(), turn.getQuestion(),
                answer, turn.getStatus(), error, sources, intent);
    }

    private ServiceException missing() { return new ServiceException("会话、消息或来源不存在或无权访问", 404); }

    private String answerErrorCode(RuntimeException failure) {
        if (failure instanceof ServiceException && failure.getMessage() != null) {
            int separator = failure.getMessage().indexOf(':');
            if (separator > 0) {
                String code = failure.getMessage().substring(0, separator);
                if (SAFE_ANSWER_ERRORS.contains(code)) return code;
            }
        }
        return "ANSWER_UNAVAILABLE";
    }

    private record Start(CustomerServiceTurn turn, CustomerServiceRelease release, boolean created) { }
    public record TurnView(String id, String requestId, int turnNo, String question, String answer,
                           String status, String errorCode, List<CustomerServiceAiGateway.SourceLabel> sources,
                           CustomerServiceIntentService.IntentView intent) { }
    public record SessionView(String id, Long appId, String releaseId, String status, Date expiresAt,
                               String welcomeMessage, List<TurnView> turns,
                               com.polaris.ai.customer.application.CustomerServiceAvatarConfig avatar,
                               com.polaris.ai.customer.application.CustomerServiceContactConfig contact) { }

    private void applyUsage(CustomerServiceTurn turn, NormalizedUsage value) {
        turn.setInputTokens(value.inputTokens());
        turn.setOutputTokens(value.outputTokens());
        turn.setTotalTokens(value.totalTokens());
        turn.setUsageSource(value.source().name());
    }

    /** 两次模型调用分别报告用量；未知保持未知，但已知部分仍结算，避免第二次失败漏计路由用量。 */
    private static final class Usage {
        private NormalizedUsage value = NormalizedUsage.unknown();
        private Integer knownTokens;
        private boolean received;
        private void add(NormalizedUsage next) {
            if (next == null) next = NormalizedUsage.unknown();
            if (next.totalTokens() != null) knownTokens = sum(knownTokens == null ? 0 : knownTokens, next.totalTokens());
            value = !received ? next : new NormalizedUsage(sum(value.inputTokens(), next.inputTokens()),
                    sum(value.outputTokens(), next.outputTokens()), sum(value.totalTokens(), next.totalTokens()),
                    null, null, null, Map.of(), value.source() == next.source() ? value.source() : com.polaris.ai.runtime.usage.UsageSource.UNKNOWN);
            received = true;
        }
        private static Integer sum(Integer first, Integer second) {
            return first == null || second == null ? null : (int) Math.min(Integer.MAX_VALUE, (long) first + second);
        }
    }
}
