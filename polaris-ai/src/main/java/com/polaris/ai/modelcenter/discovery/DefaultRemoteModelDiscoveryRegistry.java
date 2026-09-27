package com.polaris.ai.modelcenter.discovery;

import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.*;

/** 启动期校验并冻结的 Remote Model Discovery Registry。 */
@Component
public class DefaultRemoteModelDiscoveryRegistry
        implements RemoteModelDiscoveryRegistry {

    private final Map<String, RemoteModelDiscovery> discoveries;

    public DefaultRemoteModelDiscoveryRegistry(
            List<RemoteModelDiscovery> candidates) {
        Map<String, RemoteModelDiscovery> registered = new LinkedHashMap<>();
        for (RemoteModelDiscovery discovery : candidates) {
            if (discovery == null || discovery.protocolCode() == null
                    || discovery.protocolCode().isBlank()) {
                throw new IllegalStateException("Remote Model Discovery 协议不能为空");
            }
            String code = normalize(discovery.protocolCode());
            if (!code.equals(discovery.protocolCode())) {
                throw new IllegalStateException(
                        "Remote Model Discovery 协议必须是规范大写标识");
            }
            if (registered.putIfAbsent(code, discovery) != null) {
                throw new IllegalStateException(
                        "重复 Remote Model Discovery: " + code);
            }
        }
        this.discoveries = Map.copyOf(registered);
    }

    @Override
    public RemoteModelDiscovery getRequired(String protocolCode) {
        return find(protocolCode).orElseThrow(() -> new ServiceException(
                "MODEL_DISCOVERY_NOT_SUPPORTED: " + normalize(protocolCode)));
    }

    @Override
    public Optional<RemoteModelDiscovery> find(String protocolCode) {
        if (protocolCode == null || protocolCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(discoveries.get(normalize(protocolCode)));
    }

    private String normalize(String value) {
        return value == null ? "null"
                : value.trim().toUpperCase(Locale.ROOT);
    }
}
