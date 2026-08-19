package com.polaris.ai.safety.service;

import com.polaris.ai.safety.model.ModerationDictionaryVersion;
import com.polaris.ai.safety.rule.DictionarySnapshot;
import com.polaris.ai.safety.rule.DictionarySnapshotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** Idempotently initializes local moderation protection when the application is ready. */
@Component
public class DictionarySeedInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(DictionarySeedInitializer.class);

    @Autowired(required = false)
    private IDictionaryVersionService versionService;

    @Autowired(required = false)
    private DictionarySnapshotManager snapshotManager;

    @Autowired(required = false)
    private DictionarySeedSource seedSource;

    private final FailureLogLimiter failureLogLimiter =
            new FailureLogLimiter(Duration.ofMinutes(1), System::nanoTime);

    public DictionarySeedInitializer() {}

    public DictionarySeedInitializer(IDictionaryVersionService versionService,
                                     DictionarySnapshotManager snapshotManager,
                                     DictionarySeedSource seedSource) {
        this.versionService = versionService;
        this.snapshotManager = snapshotManager;
        this.seedSource = seedSource;
    }

    public void setVersionService(IDictionaryVersionService versionService) {
        this.versionService = versionService;
    }

    public void setSnapshotManager(DictionarySnapshotManager snapshotManager) {
        this.snapshotManager = snapshotManager;
    }

    public void setSeedSource(DictionarySeedSource seedSource) {
        this.seedSource = seedSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent ignored) {
        initializeIfRequired();
    }

    public void initializeIfRequired() {
        DictionarySeedSource.SeedBundle seed = seedSource.load();
        try {
            List<ModerationDictionaryVersion> published = versionService.findPublished();
            if (published.size() > 1) {
                throw new IllegalStateException(
                        "Expected at most one published dictionary before initialization");
            }
            if (!versionService.checksumExists(seed.checksum())) {
                versionService.importSeed(seed, published.isEmpty());
            }
            snapshotManager.reloadPublished();
        } catch (RuntimeException databaseFailure) {
            try {
                snapshotManager.reloadPublished();
                return;
            } catch (RuntimeException recoveryFailure) {
                databaseFailure.addSuppressed(recoveryFailure);
            }
            if (snapshotManager.current() != null) {
                logRateLimited("Moderation dictionary database reload failed; preserving last-known-good snapshot",
                        databaseFailure);
                return;
            }
            try {
                DictionarySnapshot emergency = seedSource.load().emergencySnapshot();
                snapshotManager.installEmergency(emergency);
                logRateLimited("Moderation dictionary database unavailable; installed bundled emergency snapshot "
                        + "with reserved in-memory version 0", databaseFailure);
            } catch (RuntimeException emergencyFailure) {
                LOG.error("FATAL: moderation seed could not produce a complete emergency snapshot ({})",
                        emergencyFailure.getClass().getSimpleName());
                throw new IllegalStateException(
                        "No valid moderation dictionary snapshot is available", emergencyFailure);
            }
        }
    }

    private void logRateLimited(String message, RuntimeException failure) {
        if (failureLogLimiter.allow()) {
            LOG.error("{} ({})", message, failure.getClass().getSimpleName());
        }
    }
}
