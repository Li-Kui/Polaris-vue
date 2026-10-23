package com.polaris.platform.service;

import com.polaris.ai.customer.application.CustomerServiceAiGateway;
import com.polaris.ai.customer.application.CustomerServiceConfig;
import com.polaris.ai.customer.application.CustomerServiceSpeechGateway;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.CustomerServiceApp;
import com.polaris.platform.domain.CustomerServiceRelease;
import com.polaris.platform.mapper.CustomerServiceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 客服草稿和发布；不可变快照与当前指针在同一事务内变更。 */
@Service
public class CustomerServiceConfigurationService {
    private final CustomerServiceMapper mapper;
    private final CustomerServiceAccess access;
    private final CustomerServiceAiGateway ai;
    private final ObjectMapper json;
    private final CustomerServiceCapabilityService capabilities;
    private final CustomerServiceSpeechGateway speech;
    private final CustomerServiceAvatarAssetService avatarAssets;

    public CustomerServiceConfigurationService(CustomerServiceMapper mapper, CustomerServiceAccess access,
            CustomerServiceAiGateway ai, ObjectMapper json, CustomerServiceCapabilityService capabilities, CustomerServiceSpeechGateway speech,
            CustomerServiceAvatarAssetService avatarAssets) {
        this.mapper = mapper;
        this.access = access;
        this.ai = ai;
        this.json = json;
        this.capabilities = capabilities;
        this.speech = speech;
        this.avatarAssets = avatarAssets;
    }

    public List<AppView> list() {
        return mapper.selectApps(access.requireAdmin().tenantId()).stream().map(this::view).toList();
    }

    public AppView get(Long id) {
        return view(requireApp(access.requireAdmin().tenantId(), id));
    }

    @Transactional(rollbackFor = Exception.class)
    public AppView create(CustomerServiceConfig config) {
        var actor = access.requireAdmin();
        avatarAssets.validate(config.avatar());
        var app = new CustomerServiceApp();
        app.setTenantId(actor.tenantId());
        app.setName(config.name());
        app.setConfigJson(json.writeValueAsString(config));
        app.setRevision(1L);
        app.setRuntimeEpoch(1L);
        app.setStatus("ACTIVE");
        app.setCreateBy(actor.username());
        app.setCreateTime(new Date());
        app.setUpdateTime(app.getCreateTime());
        if (mapper.insertApp(app) != 1) throw new ServiceException("创建客服失败");
        return view(app);
    }

    @Transactional(rollbackFor = Exception.class)
    public AppView update(Long id, Long revision, CustomerServiceConfig config) {
        var app = requireApp(access.requireAdmin().tenantId(), id);
        requireRevision(app, revision);
        avatarAssets.validate(config.avatar());
        app.setName(config.name());
        app.setConfigJson(json.writeValueAsString(config));
        save(app);
        return view(app);
    }

    @Transactional(rollbackFor = Exception.class)
    public AppView setStatus(Long id, Long revision, String status) {
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new ServiceException("INVALID_INPUT: 客服状态无效", 400);
        }
        var app = requireApp(access.requireAdmin().tenantId(), id);
        requireRevision(app, revision);
        if ("DISABLED".equals(status) && !"DISABLED".equals(app.getStatus())) {
            app.setRuntimeEpoch(app.getRuntimeEpoch() + 1);
        }
        app.setStatus(status);
        save(app);
        return view(app);
    }

    @Transactional(rollbackFor = Exception.class)
    public AppView publish(Long id, Long revision) {
        var actor = access.requireAdmin();
        var app = mapper.lockApp(actor.tenantId(), id);
        if (app == null) throw missing();
        requireRevision(app, revision);
        if (!"ACTIVE".equals(app.getStatus())) throw new ServiceException("客服已停用，不能发布", 409);
        var release = new CustomerServiceRelease();
        release.setId(UUID.randomUUID().toString());
        release.setTenantId(actor.tenantId());
        release.setAppId(id);
        release.setSourceRevision(app.getRevision());
        release.setConfigJson(app.getConfigJson());
        capabilities.validate(config(app.getConfigJson()).capabilities());
        // 此入口只检查元数据。模型、知识库和索引不可用时不发布。
        var config = config(app.getConfigJson());
        avatarAssets.validate(config.avatar());
        release.setModelHash(ai.validate(config));
        if (config.voice() != null && config.voice().sttModelId() != null) {
            release.setSpeechModelHash(speech.validate(config.voice().sttModelId()));
            speech.validateVocabulary(config.voice().sttModelId(),config.voice().vocabulary());
        }
        if (config.voice() != null && config.voice().ttsModelId() != null) {
            release.setPlaybackModelHash(speech.validateTts(config.voice().ttsModelId()));
        }
        release.setCreateBy(actor.username());
        release.setCreateTime(new Date());
        if (mapper.insertRelease(release) != 1) throw new ServiceException("发布客服失败");
        app.setCurrentReleaseId(release.getId());
        save(app);
        return view(app);
    }

    public CustomerServiceApp requireApp(Long tenantId, Long id) {
        var app = id == null ? null : mapper.selectApp(tenantId, id);
        if (app == null || !Objects.equals(tenantId, app.getTenantId())) throw missing();
        return app;
    }

    public CustomerServiceConfig config(String value) {
        return json.readValue(value, CustomerServiceConfig.class);
    }

    private void save(CustomerServiceApp app) {
        app.setUpdateTime(new Date());
        if (mapper.updateApp(app) != 1) throw conflict();
        app.setRevision(app.getRevision() + 1);
    }

    private void requireRevision(CustomerServiceApp app, Long revision) {
        if (revision == null || !revision.equals(app.getRevision())) throw conflict();
    }

    private AppView view(CustomerServiceApp app) {
        return new AppView(app.getId(), app.getRevision(), app.getStatus(), app.getCurrentReleaseId(),
                config(app.getConfigJson()), app.getUpdateTime());
    }

    private ServiceException missing() { return new ServiceException("客服不存在或无权访问", 404); }
    private ServiceException conflict() { return new ServiceException("VERSION_CONFLICT: 配置已变化，请刷新后重试", 409); }

    public record AppView(Long id, Long revision, String status, String currentReleaseId,
                          CustomerServiceConfig config, Date updateTime) { }
}
