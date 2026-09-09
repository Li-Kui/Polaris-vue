package com.polaris.ai.safety.rule;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Atomically exposes one immutable dictionary snapshot and its matching version. */
@Component
public class DictionarySnapshotManager {
    private final AtomicReference<CapturedSnapshot> current = new AtomicReference<>();
    private final SnapshotLoader loader;

    public DictionarySnapshotManager() {
        this(null);
    }

    @Autowired
    public DictionarySnapshotManager(SnapshotLoader loader) {
        this.loader = loader;
    }

    public CapturedSnapshot capture() {
        return current.get();
    }

    public DictionarySnapshot current() {
        CapturedSnapshot captured = capture();
        return captured == null ? null : captured.snapshot();
    }

    public void install(long version, DictionarySnapshot snapshot) {
        current.set(new CapturedSnapshot(version, snapshot));
    }

    public void installEmergency(DictionarySnapshot snapshot) {
        current.set(CapturedSnapshot.emergency(snapshot));
    }

    /** Loads into a temporary capture and publishes it with one atomic swap. */
    public long reloadPublished() {
        SnapshotLoader configuredLoader = Objects.requireNonNull(
                loader, "A published dictionary snapshot loader is required");
        CapturedSnapshot loaded = Objects.requireNonNull(
                configuredLoader.loadPublished(), "Published dictionary loader returned no snapshot");
        if (loaded.version() <= 0 || loaded.emergency()) {
            throw new IllegalStateException("Published dictionary loader returned an emergency snapshot");
        }
        current.set(loaded);
        return loaded.version();
    }

    public synchronized boolean reloadPublishedIfNewer(long candidateVersion) {
        CapturedSnapshot captured = capture();
        if (captured != null && captured.version() >= candidateVersion) {
            return false;
        }
        try {
            long reloaded = reloadPublished();
            return reloaded >= candidateVersion;
        } catch (Exception e) {
            return false;
        }
    }

    protected SnapshotLoader loader() {
        return loader;
    }

    /** Version and snapshot captured atomically for one moderation call. */
    public static final class CapturedSnapshot {
        private final long version;
        private final DictionarySnapshot snapshot;
        private final boolean emergency;

        public CapturedSnapshot(long version, DictionarySnapshot snapshot) {
            if (version <= 0) {
                throw new IllegalArgumentException("Dictionary version must be positive");
            }
            this.version = version;
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            this.emergency = false;
        }

        private CapturedSnapshot(DictionarySnapshot snapshot) {
            this.version = 0L;
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            this.emergency = true;
        }

        public static CapturedSnapshot emergency(DictionarySnapshot snapshot) {
            return new CapturedSnapshot(snapshot);
        }

        public long version() {
            return version;
        }

        public DictionarySnapshot snapshot() {
            return snapshot;
        }

        public boolean emergency() {
            return emergency;
        }
    }

    /** Task 5 extension seam for loading a fully validated published snapshot. */
    @FunctionalInterface
    public interface SnapshotLoader {
        CapturedSnapshot loadPublished();
    }
}
