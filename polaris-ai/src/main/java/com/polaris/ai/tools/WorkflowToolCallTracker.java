package com.polaris.ai.tools;

import com.polaris.ai.workflow.spi.WorkflowToolCallObserver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** 记录一次智能体节点尝试内的工具调用数量、结果和耗时。 */
public final class WorkflowToolCallTracker {

    private final WorkflowToolCallObserver observer;
    private final AtomicInteger attemptCount = new AtomicInteger();
    private final AtomicInteger callCount = new AtomicInteger();
    private final AtomicInteger succeededCount = new AtomicInteger();
    private final AtomicInteger failedCount = new AtomicInteger();
    private final AtomicInteger blockedCount = new AtomicInteger();
    private final AtomicInteger truncatedCount = new AtomicInteger();
    private final AtomicLong durationMs = new AtomicLong();

    public WorkflowToolCallTracker(WorkflowToolCallObserver observer) {
        this.observer = observer == null ? WorkflowToolCallObserver.NONE : observer;
    }

    public int reserve(String toolName, int maximum) {
        int attemptNo = attemptCount.incrementAndGet();
        while (true) {
            int current = callCount.get();
            if (current >= maximum) {
                blockedCount.incrementAndGet();
                emit(toolName, attemptNo, WorkflowToolCallObserver.Status.BLOCKED,
                        0, "TOOL_CALL_LIMIT", false);
                return -1;
            }
            if (callCount.compareAndSet(current, current + 1)) {
                int callNo = current + 1;
                emit(toolName, callNo, WorkflowToolCallObserver.Status.STARTED,
                        0, null, false);
                return callNo;
            }
        }
    }

    public void blocked(String toolName, String reasonCode) {
        int attemptNo = attemptCount.incrementAndGet();
        blockedCount.incrementAndGet();
        emit(toolName, attemptNo, WorkflowToolCallObserver.Status.BLOCKED,
                0, reasonCode, false);
    }

    public void succeeded(
            String toolName, int callNo, long elapsedMs, boolean resultTruncated) {
        succeededCount.incrementAndGet();
        durationMs.addAndGet(Math.max(0, elapsedMs));
        if (resultTruncated) truncatedCount.incrementAndGet();
        emit(toolName, callNo, WorkflowToolCallObserver.Status.SUCCEEDED,
                elapsedMs, null, resultTruncated);
    }

    public void failed(String toolName, int callNo, long elapsedMs, String reasonCode) {
        failedCount.incrementAndGet();
        durationMs.addAndGet(Math.max(0, elapsedMs));
        emit(toolName, callNo, WorkflowToolCallObserver.Status.FAILED,
                elapsedMs, reasonCode, false);
    }

    public Map<String, Number> usage() {
        Map<String, Number> usage = new LinkedHashMap<>();
        usage.put("toolAttempts", attemptCount.get());
        usage.put("toolCalls", callCount.get());
        usage.put("toolSucceeded", succeededCount.get());
        usage.put("toolFailed", failedCount.get());
        usage.put("toolBlocked", blockedCount.get());
        usage.put("toolDurationMs", durationMs.get());
        usage.put("toolResultTruncated", truncatedCount.get());
        return Map.copyOf(usage);
    }

    private void emit(
            String toolName,
            int callNo,
            WorkflowToolCallObserver.Status status,
            long elapsedMs,
            String reasonCode,
            boolean resultTruncated) {
        observer.onEvent(new WorkflowToolCallObserver.Event(
                toolName, callNo, status, elapsedMs, reasonCode, resultTruncated));
    }
}
