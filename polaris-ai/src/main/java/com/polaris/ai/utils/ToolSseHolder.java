package com.polaris.ai.utils;

import com.polaris.ai.workflow.event.WorkflowSsePublisher;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * 工具执行线程中的 SSE 发射器上下文，用于普通聊天工具向前端推送执行状态。
 */
public class ToolSseHolder {
    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    public record Context(
            SseEmitter emitter,
            WorkflowSsePublisher workflowPublisher,
            String executionId,
            String nodeId,
            AtomicBoolean workflowCancelled,
            AtomicBoolean nodeCancelled,
            BooleanSupplier cancellationProbe) {

        public boolean isWorkflow() {
            return workflowPublisher != null && executionId != null;
        }

        public boolean isCancelled() {
            return Thread.currentThread().isInterrupted()
                    || (workflowCancelled != null && workflowCancelled.get())
                    || (nodeCancelled != null && nodeCancelled.get())
                    || (cancellationProbe != null && cancellationProbe.getAsBoolean());
        }
    }

    public static void set(SseEmitter emitter) {
        HOLDER.set(new Context(emitter, null, null, null, null, null, null));
    }

    public static void setWorkflow(
            SseEmitter emitter, WorkflowSsePublisher publisher,
            String executionId, String nodeId,
            AtomicBoolean workflowCancelled, AtomicBoolean nodeCancelled) {
        setWorkflow(emitter, publisher, executionId, nodeId,
                workflowCancelled, nodeCancelled, null);
    }

    public static void setWorkflow(
            SseEmitter emitter, WorkflowSsePublisher publisher,
            String executionId, String nodeId,
            AtomicBoolean workflowCancelled, AtomicBoolean nodeCancelled,
            BooleanSupplier cancellationProbe) {
        HOLDER.set(new Context(
                emitter, publisher, executionId, nodeId,
                workflowCancelled, nodeCancelled, cancellationProbe));
    }

    public static SseEmitter get() {
        Context context = HOLDER.get();
        return context == null ? null : context.emitter();
    }

    public static Context getContext() {
        return HOLDER.get();
    }

    public static void ensureActive() {
        Context context = HOLDER.get();
        if (context != null && context.isCancelled()) {
            throw new CancellationException("AI 工具执行已取消");
        }
    }

    public static void clear() {
        HOLDER.remove();
    }
}
