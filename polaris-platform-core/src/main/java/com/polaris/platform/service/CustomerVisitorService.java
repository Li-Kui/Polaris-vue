package com.polaris.platform.service;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.core.context.CustomerServiceVisitorContext;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.CustomerServiceChannel;
import com.polaris.platform.mapper.CustomerServiceMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;

/** 访客凭证仅绑定一条会话；不创建平台账户、不代理管理员、不依赖第三方 Cookie。 */
@Service
public class CustomerVisitorService {
    private final CustomerServiceChannelService channels;
    private final CustomerServiceMapper customers;
    private final CustomerServiceConversationService conversations;
    private final CustomerVisitorRateLimiter limits;
    private final SecureRandom random = new SecureRandom();
    public CustomerVisitorService(CustomerServiceChannelService channels, CustomerServiceMapper customers,
            CustomerServiceConversationService conversations, CustomerVisitorRateLimiter limits) {
        this.channels = channels; this.customers = customers; this.conversations = conversations; this.limits = limits;
    }
    public Entry entry(String code, String remote) {
        return isolated(() -> {
            var channel = channels.requireVisitorEntry(code);
            limits.acquire(channel.getId(), "read", "channel", 600, 60);
            var config = channels.visitorConfig(channel);
            return new Entry(channel.getCode(), channel.getName(), config.name(), channel.getVisitorMessageLimit(), Boolean.TRUE.equals(channel.getVisitorSpeechEnabled()));
        });
    }
    public Created create(String code, String remote) {
        return isolated(() -> {
            var channel = channels.requireVisitorEntry(code);
            limits.acquire(channel.getId(), "create", digest(remote), 10, 60);
            limits.acquire(channel.getId(), "daily", LocalDate.now(ZoneOffset.UTC).toString(), channel.getVisitorDailySessions(), 172800);
            byte[] bytes = new byte[32]; random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            setContext(channel, UUID.randomUUID().toString(), digest(token));
            return new Created(token, conversations.createVisitor(code));
        });
    }
    public <T> T session(String code, String id, String token, boolean message, String remote, Supplier<T> action) {
        return isolated(() -> {
            if (id == null || !id.matches("[a-f0-9-]{36}") || token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw missing();
            var channel = channels.requireVisitorEntry(code);
            var session = customers.selectSession(channel.getTenantId(), 0L, id);
            String hash = digest(token);
            if (session == null || session.getVisitorTokenHash() == null
                    || !MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII), session.getVisitorTokenHash().getBytes(StandardCharsets.US_ASCII))
                    || !Objects.equals(channel.getId(), session.getChannelId())
                    || !Objects.equals(channel.getRevision(), session.getChannelRevision())
                    || !Objects.equals(channel.getReleaseId(), session.getReleaseId())
                    || !session.getExpiresAt().after(new Date())) throw missing();
            limits.acquire(channel.getId(), message ? "message" : "read", "channel", message ? 60 : 600, 60);
            if (message) limits.acquire(channel.getId(), "message-ip", digest(remote), 20, 60);
            setContext(channel, id, hash);
            return action.get();
        });
    }
    private void setContext(CustomerServiceChannel channel, String id, String hash) {
        var config = channels.visitorConfig(channel);
        boolean speech = Boolean.TRUE.equals(channel.getVisitorSpeechEnabled());
        var voice = config.voice();
        CallerContextHolder.set(new CustomerServiceVisitorContext(channel.getTenantId(), channel.getId(), id, hash,
                config.modelId(), Set.copyOf(config.knowledgeBaseIds()), channel.getVisitorMessageLimit(),
                speech && voice != null ? voice.sttModelId() : null, speech && voice != null ? voice.ttsModelId() : null,
                speech ? channel.getVisitorSpeechLimit() : 0));
    }
    /** 语音收费入口另设来源／渠道频率上限；读取、取消仍保留普通会话鉴权。 */
    public <T> T speech(String code, String id, String token, boolean charge, String remote, Supplier<T> action) {
        return session(code, id, token, false, remote, () -> {
            var visitor = (CustomerServiceVisitorContext) CallerContextHolder.require();
            if (visitor.speechLimit() == 0) throw new ServiceException("SPEECH_UNAVAILABLE: 此渠道未开放访客语音", 403);
            if (charge) {
                limits.acquire(visitor.channelId(), "speech", "channel", 30, 60);
                limits.acquire(visitor.channelId(), "speech-ip", digest(remote), 10, 60);
            }
            return action.get();
        });
    }
    private <T> T isolated(Supplier<T> work) {
        var previous = CallerContextHolder.get();
        CallerContextHolder.clear();
        try { return work.get(); } finally { CallerContextHolder.set(previous); }
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Objects.toString(value, "unknown").getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private ServiceException missing() { return new ServiceException("VISITOR_SESSION_UNAVAILABLE: 接待会话不可用，请重新开始", 404); }
    public record Entry(String code, String name, String serviceName, int messageLimit, boolean speechEnabled) { }
    public record Created(String token, CustomerServiceConversationService.SessionView session) { }
}
