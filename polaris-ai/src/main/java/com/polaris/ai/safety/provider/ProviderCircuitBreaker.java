package com.polaris.ai.safety.provider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Thread-safe global breaker for the single configured provider bean. */
public final class ProviderCircuitBreaker {
    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;
    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt;
    private boolean halfOpenProbeRunning;
    private long halfOpenProbeToken = -1;
    private long generation;
    private long nextToken;
    private final ThreadLocal<Permission> legacyPermission = new ThreadLocal<>();

    public ProviderCircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
        if (failureThreshold <= 0 || openDuration == null || openDuration.isNegative()
                || openDuration.isZero()) {
            throw new IllegalArgumentException("Circuit breaker bounds must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized boolean tryAcquirePermission() {
        Permission permission = acquirePermission();
        if (permission.permitted()) {
            legacyPermission.set(permission);
        } else {
            legacyPermission.remove();
        }
        return permission.permitted();
    }

    public synchronized Permission acquirePermission() {
        if (state == State.CLOSED) {
            return permission(State.CLOSED);
        }
        if (state == State.OPEN
                && !clock.instant().isBefore(openedAt.plus(openDuration))) {
            state = State.HALF_OPEN;
        }
        if (state == State.HALF_OPEN && !halfOpenProbeRunning) {
            halfOpenProbeRunning = true;
            Permission permission = permission(State.HALF_OPEN);
            halfOpenProbeToken = permission.token();
            return permission;
        }
        return Permission.denied();
    }

    public synchronized void recordSuccess() {
        Permission permission = legacyPermission.get();
        legacyPermission.remove();
        recordSuccess(permission);
    }

    private void reset() {
        state = State.CLOSED;
        consecutiveFailures = 0;
        openedAt = null;
        halfOpenProbeRunning = false;
        halfOpenProbeToken = -1;
    }

    public synchronized void recordSuccess(Permission permission) {
        if (!current(permission)) {
            return;
        }
        reset();
    }

    /** Releases a half-open probe when a downstream precondition denies the call. */
    public synchronized void releasePermission() {
        Permission permission = legacyPermission.get();
        legacyPermission.remove();
        release(permission);
    }

    public synchronized void release(Permission permission) {
        if (current(permission) && permission.state() == State.HALF_OPEN
                && permission.token() == halfOpenProbeToken) {
            halfOpenProbeRunning = false;
            halfOpenProbeToken = -1;
        }
    }

    public synchronized void recordFailure() {
        Permission permission = legacyPermission.get();
        legacyPermission.remove();
        recordFailure(permission);
    }

    private void fail() {
        if (state == State.HALF_OPEN) {
            open();
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            open();
        }
    }

    public synchronized void recordFailure(Permission permission) {
        if (!current(permission)) {
            return;
        }
        fail();
    }

    private void open() {
        state = State.OPEN;
        generation++;
        openedAt = clock.instant();
        halfOpenProbeRunning = false;
        halfOpenProbeToken = -1;
    }

    private Permission permission(State permissionState) {
        return new Permission(true, generation, ++nextToken, permissionState);
    }

    private boolean current(Permission permission) {
        return permission != null && permission.permitted()
                && permission.generation() == generation
                && permission.state() == state
                && (state != State.HALF_OPEN
                || permission.token() == halfOpenProbeToken);
    }

    public record Permission(
            boolean permitted, long generation, long token, State state) {
        private static Permission denied() {
            return new Permission(false, -1, -1, null);
        }
    }

    public enum State { CLOSED, OPEN, HALF_OPEN }
}
