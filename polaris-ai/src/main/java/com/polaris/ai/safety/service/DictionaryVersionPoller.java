package com.polaris.ai.safety.service;

import com.polaris.ai.safety.mapper.ModerationDictionaryVersionMapper;
import com.polaris.ai.safety.model.ModerationDictionaryVersion;
import com.polaris.ai.safety.rule.DictionarySnapshotManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DictionaryVersionPoller {
    private static final Logger log = LoggerFactory.getLogger(DictionaryVersionPoller.class);

    @Autowired(required = false)
    private ModerationDictionaryVersionMapper versionMapper;

    @Autowired(required = false)
    private DictionarySnapshotManager snapshotManager;

    private VersionSupplier versionSupplier;
    private final AtomicBoolean polling = new AtomicBoolean(false);

    public DictionaryVersionPoller() {}

    public DictionaryVersionPoller(VersionSupplier versionSupplier, DictionarySnapshotManager snapshotManager) {
        this.versionSupplier = versionSupplier;
        this.snapshotManager = snapshotManager;
    }

    @PostConstruct
    public void init() {
        if (this.versionSupplier == null && this.versionMapper != null) {
            this.versionSupplier = () -> {
                List<ModerationDictionaryVersion> published = versionMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModerationDictionaryVersion>()
                                .eq(ModerationDictionaryVersion::getStatus, "PUBLISHED")
                                .orderByDesc(ModerationDictionaryVersion::getId)
                );
                return (published != null && !published.isEmpty()) ? published.get(0).getId() : null;
            };
        }
    }

    public void setVersionMapper(ModerationDictionaryVersionMapper versionMapper) {
        this.versionMapper = versionMapper;
    }

    public void setSnapshotManager(DictionarySnapshotManager snapshotManager) {
        this.snapshotManager = snapshotManager;
    }

    public void setVersionSupplier(VersionSupplier versionSupplier) {
        this.versionSupplier = versionSupplier;
    }

    @Scheduled(fixedDelay = 60000)
    public void poll() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            if (versionSupplier == null || snapshotManager == null) {
                return;
            }
            Long latestVersion = versionSupplier.getLatestPublishedVersion();
            if (latestVersion != null && latestVersion > 0) {
                snapshotManager.reloadPublishedIfNewer(latestVersion);
            }
        } catch (Exception e) {
            log.warn("轮询词库最新版本异常: {}", e.getMessage());
        } finally {
            polling.set(false);
        }
    }

    @FunctionalInterface
    public interface VersionSupplier {
        Long getLatestPublishedVersion();
    }
}
