package com.polaris.ai.modelcenter.protocol;

import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/** 启动期校验后冻结的 Protocol Adapter Registry。 */
@Component
public class DefaultProtocolAdapterRegistry implements ProtocolAdapterRegistry {

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");

    private final Map<String, ProtocolAdapter> adapters;
    private final List<ProtocolAdapter> orderedAdapters;

    public DefaultProtocolAdapterRegistry(List<ProtocolAdapter> candidates) {
        Map<String, ProtocolAdapter> registered = new LinkedHashMap<>();
        for (ProtocolAdapter adapter : candidates) {
            validate(adapter);
            String code = normalize(adapter.protocolCode());
            if (registered.putIfAbsent(code, adapter) != null) {
                throw new IllegalStateException("重复 Protocol Adapter: " + code);
            }
        }
        this.adapters = Map.copyOf(registered);
        this.orderedAdapters = registered.values().stream()
                .sorted(Comparator.comparing(ProtocolAdapter::protocolCode))
                .toList();
    }

    @Override
    public ProtocolAdapter getRequired(String protocolCode) {
        return find(protocolCode).orElseThrow(() -> new ServiceException(
                "Protocol Adapter 不存在: " + normalize(protocolCode)));
    }

    @Override
    public Optional<ProtocolAdapter> find(String protocolCode) {
        if (protocolCode == null || protocolCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(adapters.get(normalize(protocolCode)));
    }

    @Override
    public List<ProtocolAdapter> list() {
        return orderedAdapters;
    }

    private void validate(ProtocolAdapter adapter) {
        if (adapter == null) {
            throw new IllegalStateException("Protocol Adapter 不能为空");
        }
        String code = normalize(adapter.protocolCode());
        if (!CODE_PATTERN.matcher(code).matches()
                || !code.equals(adapter.protocolCode())) {
            throw new IllegalStateException("Protocol Code 必须是规范大写标识");
        }
        if (adapter.adapterVersion() < 1) {
            throw new IllegalStateException("Protocol Adapter Version 必须是正整数");
        }
        Set<String> capabilities = adapter.mappedCapabilities();
        if (capabilities == null) {
            throw new IllegalStateException("Protocol Capability Mapping 不能为空");
        }
        for (String capability : capabilities) {
            String normalized = normalize(capability);
            if (!CODE_PATTERN.matcher(normalized).matches()
                    || !normalized.equals(capability)) {
                throw new IllegalStateException(
                        "Protocol Capability Code 必须是规范大写标识");
            }
            ProtocolEndpoint endpoint = adapter.findEndpoint(capability)
                    .orElseThrow(() -> new IllegalStateException(
                            "Protocol Capability 缺少 Endpoint: " + capability));
            if (endpoint.method() != ProtocolHttpMethod.POST) {
                throw new IllegalStateException(
                        "Capability Endpoint 第一版必须使用 POST: " + capability);
            }
        }
        adapter.modelDiscoveryEndpoint().ifPresent(endpoint -> {
            if (endpoint.method() != ProtocolHttpMethod.GET) {
                throw new IllegalStateException(
                        "Model Discovery Endpoint 必须使用 GET");
            }
        });
    }

    private String normalize(String code) {
        return code == null ? "null" : code.trim().toUpperCase(Locale.ROOT);
    }
}
