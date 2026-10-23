package com.polaris.platform.service;

import com.polaris.ai.customer.application.CustomerServiceConfig;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.auth.WorkflowShareOriginPolicy;
import com.polaris.platform.domain.CustomerServiceChannel;
import com.polaris.platform.domain.CustomerServiceSession;
import com.polaris.platform.mapper.CustomerServiceChannelMapper;
import com.polaris.platform.mapper.CustomerServiceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** 固定发布快照的接待渠道；访客需显式授权，渠道码不能代替独立会话凭证或管理员身份。 */
@Service
public class CustomerServiceChannelService {
    private final CustomerServiceChannelMapper channels;
    private final CustomerServiceMapper customers;
    private final CustomerServiceAccess access;
    private final CustomerServiceConfigurationService configurations;
    private final ObjectMapper json;

    public CustomerServiceChannelService(CustomerServiceChannelMapper channels, CustomerServiceMapper customers,
            CustomerServiceAccess access, CustomerServiceConfigurationService configurations, ObjectMapper json) {
        this.channels = channels;
        this.customers = customers;
        this.access = access;
        this.configurations = configurations;
        this.json = json;
    }

    public List<ChannelView> list(Long appId) {
        var actor = access.requireAdmin();
        configurations.requireApp(actor.tenantId(), appId);
        return channels.selectApp(actor.tenantId(), appId).stream().map(this::view).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelView create(Long appId, Settings command) {
        var actor = access.requireAdmin();
        // 与发布／停用使用同一客服行锁，限制渠道数量并固定真实发布快照。
        var app = customers.lockApp(actor.tenantId(), appId);
        if (app == null || !"ACTIVE".equals(app.getStatus()) || app.getCurrentReleaseId() == null) throw missing();
        if (channels.selectApp(actor.tenantId(), appId).size() >= 20) {
            throw new ServiceException("RATE_LIMITED: 每个客服最多 20 个渠道，请修改已有渠道", 429);
        }
        var channel = new CustomerServiceChannel();
        channel.setId(UUID.randomUUID().toString());
        channel.setCode(UUID.randomUUID().toString().replace("-", ""));
        channel.setTenantId(actor.tenantId());
        channel.setAppId(appId);
        channel.setReleaseId(app.getCurrentReleaseId());
        apply(channel, command);
        channel.setStatus("DISABLED");
        channel.setRevision(1L);
        channel.setCreateBy(actor.username());
        channel.setCreateTime(new Date());
        channel.setUpdateTime(channel.getCreateTime());
        if (channels.insert(channel) != 1) throw new ServiceException("创建客服渠道失败");
        return view(channel);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelView update(String id, Long revision, String status, Settings command) {
        var actor = access.requireAdmin();
        var channel = channels.lock(actor.tenantId(), id);
        if (channel == null) throw missing();
        if (revision == null || !revision.equals(channel.getRevision())) {
            throw new ServiceException("VERSION_CONFLICT: 渠道已变化，请刷新后重试", 409);
        }
        if (!Set.of("ACTIVE", "DISABLED").contains(status == null ? "" : status)) throw invalid();
        apply(channel, command);
        channel.setStatus(status);
        channel.setUpdateTime(new Date());
        if (channels.update(channel) != 1) throw new ServiceException("VERSION_CONFLICT: 渠道已变化", 409);
        channel.setRevision(channel.getRevision() + 1);
        return view(channel);
    }

    public EntryView entry(String code) {
        var channel = requireEntry(code);
        var config = config(channel);
        return new EntryView(channel.getCode(), channel.getName(), config.name(), channel.getReleaseId(),
                channel.getExpiresAt(), "TENANT_ADMIN", strings(channel.getCapabilityCodesJson()));
    }

    /** 必须在真实调用者上下文和创建会话事务内调用。 */
    public CustomerServiceChannel requireEntry(String code) {
        var actor = access.requireAdmin();
        if (!validCode(code)) throw missing();
        var channel = channels.selectCode(actor.tenantId(), code);
        requireActive(channel);
        var app = configurations.requireApp(actor.tenantId(), channel.getAppId());
        if (!"ACTIVE".equals(app.getStatus())) throw missing();
        return channel;
    }

    /** 预览会话不走渠道。包括旧 console 接口在内，每次读取／写入都核对持久化渠道围栏。 */
    public void requireSession(CustomerServiceSession session) { requireSession(session, false); }

    /** 管理员历史投影不依赖访客密钥有效期，仍要求原渠道授权及当前知识来源有效。 */
    public void requireVisitorRecord(CustomerServiceSession session) {
        var actor = access.requireAdmin();
        if (!Objects.equals(actor.tenantId(), session.getTenantId()) || session.getVisitorTokenHash() == null) throw unavailable();
        requireSession(session, true);
    }

    private void requireSession(CustomerServiceSession session, boolean record) {
        if (session.getChannelId() == null) return;
        // 会话访问统一按 session → app → channel 加锁，避免不同会话读取意图时与任务查询形成反向锁序。
        var app = customers.lockApp(session.getTenantId(), session.getAppId());
        var channel = channels.lock(session.getTenantId(), session.getChannelId());
        requireActive(channel);
        if (app == null || !"ACTIVE".equals(app.getStatus()) || !Objects.equals(app.getRuntimeEpoch(), session.getRuntimeEpoch())
                || !record && !session.getExpiresAt().after(new Date())) throw unavailable();
        if (!Objects.equals(channel.getRevision(), session.getChannelRevision())
                || !Objects.equals(channel.getAppId(), session.getAppId())
                || !Objects.equals(channel.getReleaseId(), session.getReleaseId())) throw unavailable();
    }

    public CustomerServiceConfig restrict(CustomerServiceSession session, CustomerServiceConfig config) {
        requireSession(session);
        if (session.getChannelId() == null) return config;
        var channel = channels.select(session.getTenantId(), session.getChannelId());
        var codes = strings(channel.getCapabilityCodesJson());
        return new CustomerServiceConfig(config.name(), config.persona(), config.welcomeMessage(), config.noAnswerMessage(),
                config.modelId(), config.knowledgeBaseIds(), config.capabilities().stream().filter(item -> codes.contains(item.code())).toList(), config.voice(), config.responseMode(), config.avatar(), config.contact());
    }

    /** 只返回公开 HTML 容器需要的来源列表；不返回客服配置、用户或知识。 */
    public List<String> embedOrigins(String code) {
        if (!validCode(code)) throw missing();
        var channel = channels.selectEmbed(code);
        requireActive(channel);
        return strings(channel.getAllowedOriginsJson());
    }

    public boolean visitorEmbed(String code) {
        if (!validCode(code)) throw missing();
        var channel = channels.selectEmbed(code);
        requireActive(channel);
        return "VISITOR".equals(channel.getAccessMode());
    }

    public CustomerServiceChannel requireVisitorEntry(String code) {
        if (!validCode(code)) throw missing();
        var channel = channels.selectEmbed(code);
        requireActive(channel);
        if (!"VISITOR".equals(channel.getAccessMode())
                || !"ACTIVE".equals(configurations.requireApp(channel.getTenantId(), channel.getAppId()).getStatus())) throw missing();
        return channel;
    }

    public CustomerServiceConfig visitorConfig(CustomerServiceChannel channel) { return config(channel); }

    private void apply(CustomerServiceChannel channel, Settings command) {
        if (command == null || command.name() == null || command.name().isBlank() || command.name().length() > 64
                || command.allowedOrigins() == null || command.allowedOrigins().size() > 10
                || command.allowedOrigins().stream().anyMatch(origin -> origin == null || origin.length() > 300 || !WorkflowShareOriginPolicy.isSafeOrigin(origin))
                || command.allowedOrigins().stream().distinct().count() != command.allowedOrigins().size()
                || command.capabilityCodes() == null || command.capabilityCodes().size() > 5
                || command.capabilityCodes().stream().anyMatch(code -> code == null || !code.matches("[a-z][a-z0-9_]{0,31}"))
                || command.capabilityCodes().stream().distinct().count() != command.capabilityCodes().size()
                || command.expiresAt() == null) throw invalid();
        Instant expiry;
        try { expiry = Instant.parse(command.expiresAt()); }
        catch (RuntimeException invalidTime) { throw invalid(); }
        if (!expiry.isAfter(Instant.now()) || expiry.isAfter(Instant.now().plus(Duration.ofDays(90)))) throw invalid();
        var config = config(channel);
        if (!config.capabilities().stream().map(item -> item.code()).toList().containsAll(command.capabilityCodes())) throw invalid();
        String mode = command.accessMode() == null ? "TENANT_ADMIN" : command.accessMode();
        int daily = command.visitorDailySessions() == null ? 200 : command.visitorDailySessions();
        int messages = command.visitorMessageLimit() == null ? 20 : command.visitorMessageLimit();
        boolean speech = Boolean.TRUE.equals(command.visitorSpeechEnabled());
        int speechLimit = command.visitorSpeechLimit() == null ? 20 : command.visitorSpeechLimit();
        if (!Set.of("TENANT_ADMIN", "VISITOR").contains(mode) || daily < 1 || daily > 10000
                || messages < 1 || messages > 50 || "VISITOR".equals(mode) && !command.capabilityCodes().isEmpty()
                || speechLimit < 1 || speechLimit > 40 || speech && (!"VISITOR".equals(mode) || config.voice() == null
                    || config.voice().sttModelId() == null && config.voice().ttsModelId() == null && !config.voice().devicePlayback())) throw invalid();
        channel.setAccessMode(mode);
        channel.setVisitorDailySessions(daily);
        channel.setVisitorMessageLimit(messages);
        channel.setVisitorSpeechEnabled(speech);
        channel.setVisitorSpeechLimit(speechLimit);
        channel.setName(command.name().strip());
        channel.setAllowedOriginsJson(json.writeValueAsString(command.allowedOrigins()));
        channel.setCapabilityCodesJson(json.writeValueAsString(command.capabilityCodes()));
        channel.setExpiresAt(Date.from(expiry));
    }

    private CustomerServiceConfig config(CustomerServiceChannel channel) {
        var release = customers.selectRelease(channel.getTenantId(), channel.getReleaseId());
        if (release == null || !channel.getAppId().equals(release.getAppId())) throw missing();
        return configurations.config(release.getConfigJson());
    }

    private ChannelView view(CustomerServiceChannel channel) {
        var config = config(channel);
        return new ChannelView(channel.getId(), channel.getCode(), channel.getName(), channel.getReleaseId(),
                channel.getStatus(), channel.getRevision(), channel.getExpiresAt(), strings(channel.getAllowedOriginsJson()),
                strings(channel.getCapabilityCodesJson()), config.capabilities().stream().map(item -> new Capability(item.code(), item.name())).toList(), channel.getAccessMode(), channel.getVisitorDailySessions(), channel.getVisitorMessageLimit(), channel.getVisitorSpeechEnabled(), channel.getVisitorSpeechLimit());
    }

    private List<String> strings(String value) { return List.of(json.readValue(value, String[].class)); }
    private boolean validCode(String code) { return code != null && code.matches("[a-f0-9]{32}"); }
    private void requireActive(CustomerServiceChannel channel) {
        if (channel == null || !"ACTIVE".equals(channel.getStatus()) || !channel.getExpiresAt().after(new Date())) throw unavailable();
    }
    private ServiceException unavailable() { return new ServiceException("CHANNEL_UNAVAILABLE: 渠道已变更、停用、到期或无权访问，请重新打开入口", 404); }
    private ServiceException missing() { return new ServiceException("客服渠道不存在或无权访问", 404); }
    private ServiceException invalid() { return new ServiceException("INVALID_INPUT: 渠道名称、精确来源、能力子集或 90 天内的到期时间无效", 400); }
    public record Settings(String name, List<String> allowedOrigins, List<String> capabilityCodes, String expiresAt,
            String accessMode, Integer visitorDailySessions, Integer visitorMessageLimit, Boolean visitorSpeechEnabled, Integer visitorSpeechLimit) {
        public Settings(String name, List<String> allowedOrigins, List<String> capabilityCodes, String expiresAt,
                String accessMode, Integer visitorDailySessions, Integer visitorMessageLimit) {
            this(name, allowedOrigins, capabilityCodes, expiresAt, accessMode, visitorDailySessions, visitorMessageLimit, false, 20);
        }
        public Settings {
            if (accessMode != null && !Set.of("TENANT_ADMIN", "VISITOR").contains(accessMode)) {
                throw new ServiceException("INVALID_INPUT: 访问方式无效", 400);
            }
        }
        public Settings(String name, List<String> allowedOrigins, List<String> capabilityCodes, String expiresAt) {
            this(name, allowedOrigins, capabilityCodes, expiresAt, "TENANT_ADMIN", 200, 20, false, 20);
        }
    }
    public record Capability(String code, String name) { }
    public record ChannelView(String id, String code, String name, String releaseId, String status, Long revision,
            Date expiresAt, List<String> allowedOrigins, List<String> capabilityCodes, List<Capability> availableCapabilities, String accessMode, Integer visitorDailySessions, Integer visitorMessageLimit, Boolean visitorSpeechEnabled, Integer visitorSpeechLimit) { }
    public record EntryView(String code, String name, String serviceName, String releaseId, Date expiresAt, String accessMode, List<String> capabilityCodes) { }
}
