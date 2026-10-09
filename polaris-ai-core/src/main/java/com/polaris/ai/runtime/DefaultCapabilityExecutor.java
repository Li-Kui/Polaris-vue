package com.polaris.ai.runtime;

import com.polaris.ai.runtime.stream.CompletedEvent;
import com.polaris.ai.runtime.stream.ErrorEvent;
import com.polaris.ai.runtime.stream.ModelStreamEvent;
import com.polaris.ai.runtime.stream.UsageEvent;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/** 固定执行许可、Adapter、Usage 与观测终态顺序的 Capability Executor。 */
public final class DefaultCapabilityExecutor implements CapabilityExecutor {

    private static final Logger log = LoggerFactory.getLogger(
            DefaultCapabilityExecutor.class);

    private final CapabilityAdapterRegistry adapterRegistry;
    private final ExecutionPermitProvider permitProvider;
    private final List<ModelExecutionObserver> observers;
    private final List<ModelUsageListener> usageListeners;

    public DefaultCapabilityExecutor(
            CapabilityAdapterRegistry adapterRegistry,
            ExecutionPermitProvider permitProvider,
            List<ModelExecutionObserver> observers,
            List<ModelUsageListener> usageListeners) {
        this.adapterRegistry = Objects.requireNonNull(
                adapterRegistry, "adapterRegistry");
        this.permitProvider = Objects.requireNonNull(
                permitProvider, "permitProvider");
        this.observers = observers == null ? List.of() : List.copyOf(observers);
        this.usageListeners = usageListeners == null
                ? List.of() : List.copyOf(usageListeners);
    }

    @Override
    public <I extends CapabilityInvocation, R> ModelExecutionResult<R> execute(
            ModelRuntimeSpec runtime,
            I invocation,
            Class<R> resultType) {
        validate(runtime, invocation);
        CapabilityAdapter<I, R> adapter = adapterRegistry.getRequired(
                invocation, resultType);
        Terminal terminal = new Terminal(runtime);
        try (ExecutionPermit permit = idempotent(
                permitProvider.acquire(runtime))) {
            ModelExecutionResult<R> result = Objects.requireNonNull(
                    adapter.execute(runtime, invocation),
                    "Capability Adapter Result");
            publishUsage(runtime, result.usage());
            terminal.success(result.usage(), result.providerRequestId());
            return result;
        } catch (RuntimeException | Error e) {
            terminal.failure(e);
            throw e;
        }
    }

    @Override
    public <I extends CapabilityInvocation> Flow.Publisher<ModelStreamEvent>
            stream(ModelRuntimeSpec runtime, I invocation) {
        validate(runtime, invocation);
        CapabilityAdapter<I, ?> adapter = adapterRegistry.getRequired(
                invocation);
        return subscriber -> subscribeStream(
                runtime, invocation, adapter, subscriber);
    }

    private <I extends CapabilityInvocation> void subscribeStream(
            ModelRuntimeSpec runtime,
            I invocation,
            CapabilityAdapter<I, ?> adapter,
            Flow.Subscriber<? super ModelStreamEvent> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        ExecutionPermit permit;
        Flow.Publisher<ModelStreamEvent> publisher;
        Terminal terminal = new Terminal(runtime);
        try {
            permit = idempotent(permitProvider.acquire(runtime));
            publisher = Objects.requireNonNull(
                    adapter.stream(runtime, invocation),
                    "Capability Stream Publisher");
        } catch (RuntimeException | Error e) {
            terminal.failure(e);
            failSubscription(subscriber, e);
            return;
        }
        try {
            publisher.subscribe(new GuardedSubscriber(
                    runtime, subscriber, permit, terminal));
        } catch (RuntimeException | Error e) {
            permit.close();
            terminal.failure(e);
            failSubscription(subscriber, e);
        }
    }

    private void validate(
            ModelRuntimeSpec runtime,
            CapabilityInvocation invocation) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(invocation, "invocation");
        if (!runtime.capabilityCode().equals(
                normalize(invocation.capabilityCode()))) {
            throw new IllegalArgumentException(
                    "Invocation Capability 与 Runtime 不匹配");
        }
    }

    private String normalize(String value) {
        return value == null ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private void publishUsage(
            ModelRuntimeSpec runtime,
            NormalizedUsage usage) {
        for (ModelUsageListener listener : usageListeners) {
            try {
                listener.onUsage(runtime, usage);
            } catch (RuntimeException e) {
                log.warn("Model usage listener failed modelId={} capability={}",
                        runtime.modelId(), runtime.capabilityCode());
            }
        }
    }

    private void observe(ModelExecutionObservation observation) {
        for (ModelExecutionObserver observer : observers) {
            try {
                observer.onTerminal(observation);
            } catch (RuntimeException e) {
                log.warn("Model execution observer failed modelId={} capability={}",
                        observation.modelId(), observation.capabilityCode());
            }
        }
    }

    private void failSubscription(
            Flow.Subscriber<? super ModelStreamEvent> subscriber,
            Throwable error) {
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long count) {
            }

            @Override
            public void cancel() {
            }
        });
        subscriber.onError(error);
    }

    private ExecutionPermit idempotent(ExecutionPermit delegate) {
        Objects.requireNonNull(delegate, "executionPermit");
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) {
                delegate.close();
            }
        };
    }

    private final class GuardedSubscriber
            implements Flow.Subscriber<ModelStreamEvent> {

        private final ModelRuntimeSpec runtime;
        private final Flow.Subscriber<? super ModelStreamEvent> downstream;
        private final ExecutionPermit permit;
        private final Terminal terminal;
        private Flow.Subscription upstream;
        private NormalizedUsage usage = NormalizedUsage.unknown();

        private GuardedSubscriber(
                ModelRuntimeSpec runtime,
                Flow.Subscriber<? super ModelStreamEvent> downstream,
                ExecutionPermit permit,
                Terminal terminal) {
            this.runtime = runtime;
            this.downstream = downstream;
            this.permit = permit;
            this.terminal = terminal;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.upstream = subscription;
            downstream.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long count) {
                    subscription.request(count);
                }

                @Override
                public void cancel() {
                    try {
                        subscription.cancel();
                    } finally {
                        permit.close();
                        terminal.cancelled();
                    }
                }
            });
        }

        @Override
        public void onNext(ModelStreamEvent event) {
            if (terminal.isDone()) {
                return;
            }
            if (event instanceof UsageEvent usageEvent) {
                usage = usageEvent.usage();
            }
            try {
                downstream.onNext(event);
            } catch (RuntimeException | Error e) {
                if (upstream != null) {
                    upstream.cancel();
                }
                permit.close();
                terminal.failure(e);
                throw e;
            }
            if (event instanceof CompletedEvent completed) {
                publishUsage(runtime, usage);
                permit.close();
                terminal.success(usage, completed.providerRequestId());
            } else if (event instanceof ErrorEvent error) {
                permit.close();
                terminal.failure(error.errorCategory(), null);
            }
        }

        @Override
        public void onError(Throwable error) {
            permit.close();
            terminal.failure(error);
            downstream.onError(error);
        }

        @Override
        public void onComplete() {
            permit.close();
            if (!terminal.isDone()) {
                IllegalStateException error = new IllegalStateException(
                        "MODEL_STREAM_TERMINAL_EVENT_MISSING");
                terminal.failure(error);
                downstream.onError(error);
                return;
            }
            downstream.onComplete();
        }
    }

    private final class Terminal {

        private final ModelRuntimeSpec runtime;
        private final String traceId = UUID.randomUUID().toString();
        private final long startedAtNanos = System.nanoTime();
        private final AtomicBoolean done = new AtomicBoolean();

        private Terminal(ModelRuntimeSpec runtime) {
            this.runtime = runtime;
        }

        private boolean isDone() {
            return done.get();
        }

        private void success(
                NormalizedUsage usage,
                String providerRequestId) {
            complete(true, null, providerRequestId, usage);
        }

        private void failure(Throwable error) {
            String category = error instanceof CategorizedModelExecutionError typed
                    ? typed.errorCategory()
                    : error.getClass().getSimpleName();
            String requestId = error instanceof CategorizedModelExecutionError typed
                    ? typed.providerRequestId() : null;
            complete(false, category, requestId, NormalizedUsage.unknown());
        }

        private void failure(String category, String providerRequestId) {
            complete(false, category, providerRequestId,
                    NormalizedUsage.unknown());
        }

        private void cancelled() {
            complete(false, "CANCELLED", null, NormalizedUsage.unknown());
        }

        private void complete(
                boolean success,
                String errorCategory,
                String providerRequestId,
                NormalizedUsage usage) {
            if (!done.compareAndSet(false, true)) {
                return;
            }
            observe(new ModelExecutionObservation(
                    traceId, runtime.modelId(), runtime.modelCode(),
                    runtime.modelRevision(), runtime.connectionId(),
                    runtime.connectionRevision(), runtime.providerCode(),
                    runtime.protocolCode(), runtime.capabilityCode(),
                    runtime.activeFeatures(),
                    Math.max(0L, (System.nanoTime() - startedAtNanos)
                            / 1_000_000L),
                    success, errorCategory, providerRequestId, usage));
        }
    }
}
