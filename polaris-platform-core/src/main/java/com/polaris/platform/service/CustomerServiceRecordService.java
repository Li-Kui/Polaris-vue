package com.polaris.platform.service;

import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.CustomerServiceApp;
import com.polaris.platform.domain.CustomerServiceSession;
import com.polaris.platform.mapper.CustomerServiceMapper;
import com.polaris.platform.mapper.CustomerServiceOperationMapper;
import com.polaris.platform.mapper.CustomerServiceSpeechMapper;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 本人测试及本客服访客会话的只读记录；运行账本与可见正文分别授权，不加载音频或执行工作流。 */
@Service
public class CustomerServiceRecordService {
    private final CustomerServiceAccess access;
    private final CustomerServiceMapper customers;
    private final CustomerServiceSpeechMapper speeches;
    private final CustomerServiceOperationMapper operations;
    private final CustomerServiceConfigurationService configurations;
    private final CustomerServiceConversationService conversations;

    public CustomerServiceRecordService(CustomerServiceAccess access, CustomerServiceMapper customers,
            CustomerServiceSpeechMapper speeches, CustomerServiceOperationMapper operations,
            CustomerServiceConfigurationService configurations, CustomerServiceConversationService conversations) {
        this.access = access;
        this.customers = customers;
        this.speeches = speeches;
        this.operations = operations;
        this.configurations = configurations;
        this.conversations = conversations;
    }

    public Page list(Long appId, String beforeId) {
        var actor = access.requireAdmin();
        var app = configurations.requireApp(actor.tenantId(), appId);
        var before = beforeId == null ? null : owned(actor, appId, beforeId);
        var rows = customers.selectSessionRecords(actor.tenantId(), actor.userId(), appId, before);
        var now = new Date();
        var items = rows.stream().limit(20).map(row -> summary(row, app, now)).toList();
        if (!actor.equals(access.requireAdmin())) throw missing();
        return new Page(items, rows.size() > 20 ? items.get(items.size() - 1).id() : null);
    }

    public Detail detail(Long appId, String sessionId) {
        var actor = access.requireAdmin();
        var app = configurations.requireApp(actor.tenantId(), appId);
        var session = owned(actor, appId, sessionId);
        var turns = customers.selectTurns(actor.tenantId(), sessionId);
        var speech = speeches.selectSession(actor.tenantId(), sessionId);
        int operationCount = operations.countSession(actor.tenantId(), sessionId);
        Map<String, CustomerServiceConversationService.TurnView> content = Map.of();
        boolean contentAvailable = true;
        try {
            content = (session.getVisitorTokenHash() == null ? conversations.recordContent(sessionId) : conversations.visitorRecordContent(sessionId)).stream()
                    .collect(Collectors.toMap(CustomerServiceConversationService.TurnView::id, Function.identity()));
        } catch (RuntimeException unavailable) {
            // 不将权限、渠道、知识来源或服务异常作为历史正文的读取后门。
            contentAvailable = false;
        }
        var visible = content;
        var now = new Date();
        var turnViews = turns.stream().map(turn -> {
            var body = visible.get(turn.getId());
            return new Turn(turn.getId(), turn.getRequestId(), turn.getTurnNo(), turn.getStatus(), turn.getErrorCode(),
                    turn.getCreateTime(), turn.getFinishedTime(), elapsed(turn.getCreateTime(), turn.getFinishedTime()),
                    overdue(turn.getStatus(), turn.getDeadline(), now), turn.getInputTokens(), turn.getOutputTokens(),
                    turn.getUsageSource(), body == null ? null : body.question(), body == null ? null : body.answer(),
                    body == null ? List.<com.polaris.ai.customer.application.CustomerServiceAiGateway.SourceLabel>of() : body.sources(),
                    body == null ? null : body.errorCode());
        }).toList();
        // 识别正文没有逐条来源指针；历史页不额外返回它，已发送文字由上面的问答授权投影提供。
        var speechViews = speech.stream().map(item -> new Speech(item.getRequestId(), item.getKind(), item.getTurnId(),
                item.getStatus(), item.getErrorCode(), item.getAudioMillis(), item.getCreateTime(), item.getFinishedTime(),
                elapsed(item.getCreateTime(), item.getFinishedTime()), overdue(item.getStatus(), item.getDeadline(), now), item.getPartNo())).toList();
        if (!actor.equals(access.requireAdmin())) throw missing();
        return new Detail(summary(session, app, now), contentAvailable, turnViews, speechViews, operationCount);
    }

    private CustomerServiceSession owned(CustomerServiceAccess.Actor actor, Long appId, String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw missing();
        var session = customers.selectSession(actor.tenantId(), actor.userId(), id);
        if (session == null) {
            session = customers.selectSession(actor.tenantId(), 0L, id);
            if (session != null && session.getVisitorTokenHash() == null) throw missing();
        }
        if (session == null || !Objects.equals(session.getAppId(), appId)
                || !Objects.equals(session.getTenantId(), actor.tenantId()) || !(Objects.equals(session.getUserId(), actor.userId()) || Objects.equals(session.getUserId(), 0L) && session.getVisitorTokenHash() != null)) throw missing();
        return session;
    }

    private Summary summary(CustomerServiceSession session, CustomerServiceApp app, Date now) {
        String state = session.getStatus();
        if ("ACTIVE".equals(state)) {
            if (!Objects.equals(app.getRuntimeEpoch(), session.getRuntimeEpoch())) state = "CLOSED";
            else if (!session.getExpiresAt().after(now)) state = "EXPIRED";
        }
        return new Summary(session.getId(), session.getReleaseId(), session.getVisitorTokenHash() != null ? "VISITOR" : session.getChannelId() == null ? "CONSOLE" : "CHANNEL",
                state, session.getTurnCount(), session.getCreateTime(), session.getExpiresAt());
    }

    private static Long elapsed(Date start, Date end) {
        return start == null || end == null || end.before(start) ? null : end.getTime() - start.getTime();
    }
    private static boolean overdue(String status, Date deadline, Date now) {
        return "RUNNING".equals(status) && deadline != null && !deadline.after(now);
    }
    private ServiceException missing() { return new ServiceException("接待记录不存在或无权访问", 404); }

    public record Page(List<Summary> items, String nextCursor) { }
    public record Summary(String id, String releaseId, String entry, String status, Integer turnCount, Date createTime, Date expiresAt) { }
    public record Detail(Summary session, boolean contentAvailable, List<Turn> turns, List<Speech> speech, int operationCount) { }
    public record Turn(String id, String requestId, Integer turnNo, String status, String errorCode, Date createTime,
                       Date finishedTime, Long elapsedMillis, boolean overdue, Integer inputTokens, Integer outputTokens,
                       String usageSource, String question, String answer,
                       List<com.polaris.ai.customer.application.CustomerServiceAiGateway.SourceLabel> sources, String contentErrorCode) { }
    public record Speech(String requestId, String kind, String turnId, String status, String errorCode, Integer audioMillis,
                         Date createTime, Date finishedTime, Long elapsedMillis, boolean overdue, Integer partNo) { }
}
