package com.polaris.ai.agent.collaboration;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 单运行绝对预算和取消探针；不持有线程池，不提供超时后接管或重放。 */
public final class AgentCallControl implements AutoCloseable {
    private final long deadline;
    private final Runnable activeCheck;
    private final AtomicReference<Runnable> cancellation = new AtomicReference<>();
    public AgentCallControl(Duration budget, Runnable activeCheck) {
        this.deadline = System.nanoTime() + Objects.requireNonNull(budget).toNanos();
        this.activeCheck = Objects.requireNonNull(activeCheck);
    }
    public void check() {
        try {
            if (Thread.currentThread().isInterrupted()) throw new Failure(Code.CANCELLED);
            activeCheck.run();
            if (deadline - System.nanoTime() <= 0) throw new Failure(Code.DEADLINE_EXCEEDED);
        } catch (RuntimeException failure) { close(); throw failure; }
    }
    public long remainingMillis() {
        check();
        return Math.max(1, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
    }
    public void registerCancellation(Runnable action) {
        if (!cancellation.compareAndSet(null, Objects.requireNonNull(action))) throw new Failure(Code.INVALID_REQUEST);
        check();
    }
    public void clearCancellation(Runnable action) { cancellation.compareAndSet(action, null); }
    @Override public void close() {
        Runnable action = cancellation.getAndSet(null);
        if (action != null) {
            try { action.run(); } catch (RuntimeException ignored) { /* 不覆盖原始固定错误码。 */ }
        }
    }
}
