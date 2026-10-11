package com.polaris.platform.mcp;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** 实例级保护：全局16并发、租户4并发/每分钟30次；不宣称跨副本配额。 */
@Component
public class McpInvocationLimiter {
    private final Map<Long, Bucket> tenants = new HashMap<>();
    private final LongSupplier clock;
    private int active;

    public McpInvocationLimiter() { this(System::nanoTime); }
    McpInvocationLimiter(LongSupplier clock) { this.clock = clock; }

    public synchronized Lease acquire(Long tenantId) {
        long now = clock.getAsLong();
        long window = TimeUnit.MINUTES.toNanos(1);
        tenants.entrySet().removeIf(entry -> entry.getValue().active == 0 && now - entry.getValue().start >= window);
        Bucket bucket = tenants.get(tenantId);
        if (bucket == null) {
            if (tenants.size() >= 4096) throw limited();
            bucket = new Bucket(now);
            tenants.put(tenantId, bucket);
        }
        if (now - bucket.start >= window) { bucket.start = now; bucket.count = 0; }
        if (active >= 16 || bucket.active >= 4 || bucket.count >= 30) throw limited();
        bucket.count++;
        bucket.active++;
        active++;
        return new Lease(bucket);
    }

    private McpInvocationException limited() { return new McpInvocationException(McpInvocationException.Code.RATE_LIMITED); }

    private static class Bucket {
        long start;
        int count;
        int active;
        Bucket(long start) { this.start = start; }
    }

    public final class Lease implements AutoCloseable {
        private final Bucket bucket;
        private boolean closed;
        private Lease(Bucket bucket) { this.bucket = bucket; }
        @Override public void close() {
            synchronized (McpInvocationLimiter.this) {
                if (!closed) { closed = true; active--; bucket.active--; }
            }
        }
    }
}
