package com.polaris.ai.runtime;

import java.util.*;
import java.util.regex.Pattern;

/** 启动期冻结并拒绝重复映射的 Capability Adapter Registry。 */
public final class DefaultCapabilityAdapterRegistry
        implements CapabilityAdapterRegistry {

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");

    private final Map<AdapterKey, CapabilityAdapter<?, ?>> adapters;

    public DefaultCapabilityAdapterRegistry(
            List<CapabilityAdapter<?, ?>> candidates) {
        Map<AdapterKey, CapabilityAdapter<?, ?>> registered =
                new LinkedHashMap<>();
        for (CapabilityAdapter<?, ?> adapter
                : candidates == null
                ? List.<CapabilityAdapter<?, ?>>of() : candidates) {
            validate(adapter);
            AdapterKey key = new AdapterKey(
                    adapter.capabilityCode(), adapter.invocationType());
            if (registered.putIfAbsent(key, adapter) != null) {
                throw new IllegalStateException(
                        "重复 Capability Adapter: " + adapter.capabilityCode()
                                + "/" + adapter.invocationType().getName());
            }
        }
        this.adapters = Map.copyOf(registered);
    }

    @Override
    public <I extends CapabilityInvocation, R> CapabilityAdapter<I, R>
            getRequired(I invocation, Class<R> resultType) {
        CapabilityAdapter<I, ?> adapter = getRequired(invocation);
        if (!adapter.resultType().equals(resultType)) {
            throw new IllegalArgumentException(
                    "Capability Adapter Result Type 不匹配");
        }
        @SuppressWarnings("unchecked")
        CapabilityAdapter<I, R> typed =
                (CapabilityAdapter<I, R>) adapter;
        return typed;
    }

    @Override
    public <I extends CapabilityInvocation> CapabilityAdapter<I, ?>
            getRequired(I invocation) {
        Objects.requireNonNull(invocation, "invocation");
        String capability = normalize(invocation.capabilityCode());
        CapabilityAdapter<?, ?> adapter = adapters.get(
                new AdapterKey(capability, invocation.getClass()));
        if (adapter == null) {
            throw new IllegalStateException(
                    "Capability Adapter 不存在: " + capability
                            + "/" + invocation.getClass().getName());
        }
        @SuppressWarnings("unchecked")
        CapabilityAdapter<I, ?> typed =
                (CapabilityAdapter<I, ?>) adapter;
        return typed;
    }

    private void validate(CapabilityAdapter<?, ?> adapter) {
        Objects.requireNonNull(adapter, "adapter");
        String normalized = normalize(adapter.capabilityCode());
        if (!CODE_PATTERN.matcher(normalized).matches()
                || !normalized.equals(adapter.capabilityCode())) {
            throw new IllegalStateException(
                    "Capability Adapter Code 必须是规范大写标识");
        }
        Objects.requireNonNull(adapter.invocationType(), "invocationType");
        Objects.requireNonNull(adapter.resultType(), "resultType");
    }

    private String normalize(String value) {
        return value == null ? "null"
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private record AdapterKey(
            String capabilityCode,
            Class<?> invocationType) {
    }
}
