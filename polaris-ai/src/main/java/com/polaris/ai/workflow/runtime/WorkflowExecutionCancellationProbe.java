package com.polaris.ai.workflow.runtime;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** 节流查询数据库租约，使跨实例取消能被运行节点快速感知。 */
@Slf4j
public final class WorkflowExecutionCancellationProbe implements BooleanSupplier {

    private final WorkflowExecutionStore executionStore;
    private final String executionId;
    private final AtomicBoolean localCancelled;
    private final long checkIntervalNanos;
    private final AtomicLong nextCheckNanos = new AtomicLong(0L);
    private volatile boolean stopped;

    public WorkflowExecutionCancellationProbe(
            WorkflowExecutionStore executionStore,
            String executionId,
            AtomicBoolean localCancelled,
            Duration checkInterval) {
        this.executionStore = executionStore;
        this.executionId = executionId;
        this.localCancelled = localCancelled;
        this.checkIntervalNanos = Math.max(1L, checkInterval.toNanos());
    }

    @Override
    public boolean getAsBoolean() {
        if (stopped || localCancelled.get() || Thread.currentThread().isInterrupted()) {
            return true;
        }
        long now = System.nanoTime();
        long scheduled = nextCheckNanos.get();
        if (now < scheduled || !nextCheckNanos.compareAndSet(scheduled, now + checkIntervalNanos)) {
            return stopped || localCancelled.get();
        }
        try {
            if (!executionStore.hasActiveLease(executionId)) {
                stopped = true;
                localCancelled.set(true);
            }
        } catch (RuntimeException e) {
            // 失去数据库状态确认时停止副作用，避免在无租约状态下继续运行。
            stopped = true;
            localCancelled.set(true);
            log.warn("工作流取消探针读取执行状态失败，已停止本地任务: {}", executionId, e);
        }
        return stopped || localCancelled.get();
    }

    public void ensureActive() {
        if (getAsBoolean()) {
            throw new CancellationException("工作流执行已取消或租约已失效");
        }
    }
}
