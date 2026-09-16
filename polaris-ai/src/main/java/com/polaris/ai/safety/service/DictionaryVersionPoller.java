package com.polaris.ai.safety.service;

import com.polaris.ai.safety.config.ModerationProperties;
import com.polaris.ai.safety.mapper.ModerationDictionaryVersionMapper;
import com.polaris.ai.safety.rule.DictionarySnapshotManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Event-first dictionary refresh with an adaptive database safety reconciliation. */
@Component
public class DictionaryVersionPoller {
    private static final Logger log = LoggerFactory.getLogger(DictionaryVersionPoller.class);
    private static final long DEFAULT_INITIAL_DELAY_MS = 300_000L;
    private static final long DEFAULT_MAX_DELAY_MS = 1_800_000L;

    @Autowired(required = false)
    private ModerationDictionaryVersionMapper versionMapper;

    @Autowired(required = false)
    private DictionarySnapshotManager snapshotManager;

    @Autowired(required = false)
    @Qualifier("scheduledExecutorService")
    private ScheduledExecutorService timerExecutor;

    @Autowired(required = false)
    private ModerationProperties properties;

    private VersionSupplier versionSupplier;
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final AtomicBoolean reconcileRequested = new AtomicBoolean(false);
    private final AtomicLong idleDelayMs = new AtomicLong(DEFAULT_INITIAL_DELAY_MS);
    private final Object timerLock = new Object();
    private ScheduledFuture<?> nextTask;
    private long nextTaskAt = Long.MAX_VALUE;

    public DictionaryVersionPoller() {}

    public DictionaryVersionPoller(
            VersionSupplier versionSupplier,
            DictionarySnapshotManager snapshotManager) {
        this.versionSupplier = versionSupplier;
        this.snapshotManager = snapshotManager;
    }

    @PostConstruct
    public void init() {
        if (versionSupplier == null && versionMapper != null) {
            versionSupplier = versionMapper::selectPublishedVersionId;
        }
        idleDelayMs.set(initialDelayMs());
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

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent ignored) {
        long initial = initialDelayMs();
        idleDelayMs.set(doubleDelay(initial));
        schedule(initial);
    }

    @EventListener
    public void onReconcileRequested(DictionaryVersionReconcileRequested ignored) {
        requestReconcile();
    }

    public void requestReconcile() {
        idleDelayMs.set(initialDelayMs());
        reconcileRequested.set(true);
        schedule(0L);
    }

    public void poll() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        reconcileRequested.set(false);
        boolean changed = false;
        boolean failed = false;
        try {
            if (versionSupplier == null || snapshotManager == null) {
                return;
            }
            Long latestVersion = versionSupplier.getLatestPublishedVersion();
            if (latestVersion != null && latestVersion > 0) {
                changed = snapshotManager.reloadPublishedIfChangedOrThrow(latestVersion);
            }
        } catch (Exception e) {
            failed = true;
            log.warn("轮询词库最新版本异常: {}", e.getMessage());
        } finally {
            polling.set(false);
            if (reconcileRequested.getAndSet(false)) {
                idleDelayMs.set(initialDelayMs());
                schedule(0L);
            } else {
                schedule(nextDelay(changed, failed));
            }
        }
    }

    private long nextDelay(boolean changed, boolean failed) {
        long initial = initialDelayMs();
        if (changed || failed) {
            idleDelayMs.set(initial);
            return initial;
        }
        long current = Math.max(initial, idleDelayMs.get());
        long next = doubleDelay(current);
        idleDelayMs.set(Math.min(maxDelayMs(), next));
        return Math.min(current, maxDelayMs());
    }

    private long doubleDelay(long delayMs) {
        return delayMs > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : delayMs * 2L;
    }

    private void schedule(long delayMs) {
        if (timerExecutor == null) return;
        long wakeAt = System.currentTimeMillis() + Math.max(0L, delayMs);
        synchronized (timerLock) {
            if (nextTask != null && nextTaskAt <= wakeAt) return;
            if (nextTask != null) nextTask.cancel(false);
            nextTaskAt = wakeAt;
            try {
                nextTask = timerExecutor.schedule(() -> {
                    synchronized (timerLock) {
                        nextTask = null;
                        nextTaskAt = Long.MAX_VALUE;
                    }
                    poll();
                }, Math.max(0L, wakeAt - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            } catch (RuntimeException e) {
                nextTask = null;
                nextTaskAt = Long.MAX_VALUE;
                log.warn("无法调度词库版本安全核对", e);
            }
        }
    }

    private long initialDelayMs() {
        return properties == null
                ? DEFAULT_INITIAL_DELAY_MS : properties.getSnapshotReconcileInitialMs();
    }

    private long maxDelayMs() {
        return properties == null
                ? DEFAULT_MAX_DELAY_MS : properties.getSnapshotReconcileMaxMs();
    }

    @FunctionalInterface
    public interface VersionSupplier {
        Long getLatestPublishedVersion();
    }
}
