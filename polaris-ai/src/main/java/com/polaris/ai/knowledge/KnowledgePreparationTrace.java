package com.polaris.ai.knowledge;

/** 客服单次输入准备的无正文计时；不缓存查询、证据、授权或模型，不传播到异步线程。 */
public final class KnowledgePreparationTrace implements AutoCloseable {
    private static final ThreadLocal<KnowledgePreparationTrace> CURRENT = new ThreadLocal<>();
    private final Thread owner = Thread.currentThread();
    private final KnowledgePreparationTrace previous;
    private int calls;
    private long resolveMs, embeddingMs, searchMs, mountMs;
    private boolean closed;

    private KnowledgePreparationTrace() { previous=CURRENT.get();CURRENT.set(this); }
    public static KnowledgePreparationTrace open() { return new KnowledgePreparationTrace(); }
    public static void retrieval(long resolveMs, long embeddingMs, long searchMs, long mountMs) {
        var current = CURRENT.get();
        if (current == null) return;
        current.calls++;current.resolveMs+=Math.max(0,resolveMs);current.embeddingMs+=Math.max(0,embeddingMs);
        current.searchMs+=Math.max(0,searchMs);current.mountMs+=Math.max(0,mountMs);
    }
    public Snapshot snapshot() { return new Snapshot(calls,calls==0?null:resolveMs,calls==0?null:embeddingMs,calls==0?null:searchMs,calls==0?null:mountMs); }
    @Override public void close() {
        if (closed) return;
        if (owner != Thread.currentThread() || CURRENT.get() != this) throw new IllegalStateException("KNOWLEDGE_PREPARATION_SCOPE_ORDER");
        closed=true;if (previous == null) CURRENT.remove();else CURRENT.set(previous);
    }
    public record Snapshot(int calls, Long resolveMs, Long embeddingMs, Long searchMs, Long mountMs) { }
}
