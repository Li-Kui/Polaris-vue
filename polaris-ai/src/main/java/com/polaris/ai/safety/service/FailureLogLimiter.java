package com.polaris.ai.safety.service;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Thread-safe monotonic-window limiter for repeated bootstrap failure logs. */
public final class FailureLogLimiter {
    private final long intervalNanos;
    private final LongSupplier nanoTime;
    private final AtomicLong lastAllowed = new AtomicLong(Long.MIN_VALUE);

    public FailureLogLimiter(Duration interval, LongSupplier nanoTime) {
        this.intervalNanos = Objects.requireNonNull(interval, "interval").toNanos();
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (intervalNanos <= 0) {
            throw new IllegalArgumentException("Failure log interval must be positive");
        }
    }

    public boolean allow() {
        long now = nanoTime.getAsLong();
        while (true) {
            long prior = lastAllowed.get();
            if (prior != Long.MIN_VALUE && now - prior < intervalNanos) {
                return false;
            }
            if (lastAllowed.compareAndSet(prior, now)) {
                return true;
            }
        }
    }
}
