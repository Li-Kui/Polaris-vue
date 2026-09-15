package com.polaris.ai.observability;

import dev.langchain4j.model.chat.response.ChatResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 统一记录 AI 模型、工具、向量检索和运行终态指标。 */
@Component
public class AiObservability
{
    private static volatile MeterRegistry registry;

    public AiObservability(ObjectProvider<MeterRegistry> registryProvider)
    {
        registry = registryProvider.getIfAvailable();
    }

    public static long start()
    {
        return System.nanoTime();
    }

    public static void recordFirstToken(
            String provider, String model, long startedAtNanos)
    {
        MeterRegistry current = registry;
        if (current == null) return;
        Timer.builder("polaris.ai.chat.first.token")
                .description("AI 对话首 Token 延迟")
                .tags("provider", tag(provider), "model", tag(model))
                .register(current)
                .record(elapsedNanos(startedAtNanos), TimeUnit.NANOSECONDS);
    }

    public static void recordChat(
            String provider,
            String model,
            long startedAtNanos,
            ChatResponse response,
            Throwable error)
    {
        MeterRegistry current = registry;
        if (current == null) return;
        String outcome = error == null ? "success" : "failure";
        Timer.builder("polaris.ai.chat.duration")
                .description("AI 对话请求总耗时")
                .tags("provider", tag(provider), "model", tag(model), "outcome", outcome)
                .register(current)
                .record(elapsedNanos(startedAtNanos), TimeUnit.NANOSECONDS);
        Counter.builder("polaris.ai.chat.requests")
                .description("AI 对话请求数量")
                .tags("provider", tag(provider), "model", tag(model), "outcome", outcome)
                .register(current)
                .increment();
        if (error != null) {
            recordTerminal(classify(error));
            return;
        }
        if (response != null && response.tokenUsage() != null) {
            incrementToken(current, provider, model, "input",
                    response.tokenUsage().inputTokenCount());
            incrementToken(current, provider, model, "output",
                    response.tokenUsage().outputTokenCount());
            incrementToken(current, provider, model, "total",
                    response.tokenUsage().totalTokenCount());
        }
    }

    public static void recordEmbedding(
            String provider,
            String model,
            int batchSize,
            long startedAtNanos,
            Throwable error)
    {
        MeterRegistry current = registry;
        if (current == null) return;
        String outcome = error == null ? "success" : "failure";
        Timer.builder("polaris.ai.embedding.duration")
                .description("Embedding 请求耗时")
                .tags("provider", tag(provider), "model", tag(model), "outcome", outcome)
                .register(current)
                .record(elapsedNanos(startedAtNanos), TimeUnit.NANOSECONDS);
        current.summary("polaris.ai.embedding.batch.size",
                        "provider", tag(provider), "model", tag(model))
                .record(Math.max(0, batchSize));
    }

    public static void recordTool(
            String toolName, long durationMillis, String outcome, String failureType)
    {
        MeterRegistry current = registry;
        if (current == null) return;
        Timer.builder("polaris.ai.tool.duration")
                .description("AI 工具调用耗时")
                .tags("tool", tag(toolName), "outcome", tag(outcome),
                        "failure", tag(failureType))
                .register(current)
                .record(Math.max(0, durationMillis), TimeUnit.MILLISECONDS);
        Counter.builder("polaris.ai.tool.calls")
                .description("AI 工具调用数量")
                .tags("tool", tag(toolName), "outcome", tag(outcome),
                        "failure", tag(failureType))
                .register(current)
                .increment();
    }

    public static void recordQdrant(
            String operation, String collection, long startedAtNanos, Throwable error)
    {
        MeterRegistry current = registry;
        if (current == null) return;
        Timer.builder("polaris.ai.qdrant.duration")
                .description("Qdrant 操作耗时")
                .tags("operation", tag(operation), "collection", tag(collection),
                        "outcome", error == null ? "success" : "failure")
                .register(current)
                .record(elapsedNanos(startedAtNanos), TimeUnit.NANOSECONDS);
    }

    public static void recordTerminal(String type)
    {
        MeterRegistry current = registry;
        if (current == null) return;
        current.counter("polaris.ai.terminal.events", "type", tag(type)).increment();
    }

    public static void bindGauge(String name, String cache, Supplier<Number> value)
    {
        MeterRegistry current = registry;
        if (current == null) return;
        Gauge.builder(name, value, supplier -> supplier.get().doubleValue())
                .description("AI 模型缓存当前实例数")
                .tag("cache", cache)
                .strongReference(true)
                .register(current);
    }

    private static void incrementToken(
            MeterRegistry current,
            String provider,
            String model,
            String type,
            Integer count)
    {
        if (count == null || count <= 0) return;
        current.counter("polaris.ai.chat.tokens",
                        "provider", tag(provider), "model", tag(model), "type", type)
                .increment(count);
    }

    private static long elapsedNanos(long startedAtNanos)
    {
        return Math.max(0, System.nanoTime() - startedAtNanos);
    }

    private static String classify(Throwable error)
    {
        if (error == null) return "none";
        String type = error.getClass().getSimpleName().toLowerCase();
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        if (type.contains("cancel") || message.contains("取消")) return "cancelled";
        if (type.contains("timeout") || message.contains("超时")) return "timeout";
        if (message.contains("内容") && (message.contains("过滤") || message.contains("安全"))) {
            return "content_filtered";
        }
        if (message.contains("限流") || message.contains("quota") || message.contains("rate limit")) {
            return "rate_limited";
        }
        return "failed";
    }

    private static String tag(String value)
    {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
