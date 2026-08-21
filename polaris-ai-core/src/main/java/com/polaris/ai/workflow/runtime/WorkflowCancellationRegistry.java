package com.polaris.ai.workflow.runtime;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** 进程内取消信号；最终状态仍以数据库为准。 */
@Component
public class WorkflowCancellationRegistry {

    private final ConcurrentHashMap<String, Entry> executions = new ConcurrentHashMap<>();

    private record Entry(AtomicBoolean signal, AtomicReference<Future<?>> future) {
    }

    public AtomicBoolean register(String executionId) {
        AtomicBoolean signal = new AtomicBoolean(false);
        Entry previous = executions.putIfAbsent(
                executionId, new Entry(signal, new AtomicReference<>()));
        if (previous != null) {
            throw new IllegalStateException("工作流执行已在运行");
        }
        return signal;
    }

    public void attachFuture(String executionId, Future<?> future) {
        Entry entry = executions.get(executionId);
        if (entry == null) {
            future.cancel(true);
            return;
        }
        entry.future().set(future);
        if (entry.signal().get()) {
            future.cancel(true);
        }
    }

    public boolean cancel(String executionId) {
        Entry entry = executions.get(executionId);
        if (entry == null) {
            return false;
        }
        boolean changed = !entry.signal().getAndSet(true);
        Future<?> future = entry.future().get();
        if (future != null) {
            future.cancel(true);
        }
        return changed;
    }

    public void unregister(String executionId) {
        executions.remove(executionId);
    }

    public Set<String> activeExecutionIds() {
        return Set.copyOf(executions.keySet());
    }
}
