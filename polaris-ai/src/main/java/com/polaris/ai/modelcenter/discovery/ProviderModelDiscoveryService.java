package com.polaris.ai.modelcenter.discovery;

import com.polaris.ai.modelcenter.protocol.ProtocolAdapter;
import com.polaris.ai.modelcenter.protocol.ProtocolAdapterRegistry;
import com.polaris.ai.modelcenter.protocol.ProtocolEndpoint;
import com.polaris.ai.modelcenter.schema.ProviderProfileValueValidator;
import com.polaris.ai.modelcenter.schema.SchemaProfileDefinition;
import com.polaris.ai.modelcenter.service.IAiProviderConnectionService;
import com.polaris.ai.modelcenter.vo.ProviderConnectionRuntime;
import com.polaris.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 权限感知的已保存 Connection 模型发现服务。 */
@Service
public class ProviderModelDiscoveryService {

    static final Duration CACHE_TTL = Duration.ofSeconds(60);
    static final int MAX_CACHE_ENTRIES = 1024;

    private final IAiProviderConnectionService connectionService;
    private final ProviderProfileValueValidator profileValidator;
    private final ProtocolAdapterRegistry protocolRegistry;
    private final RemoteModelDiscoveryRegistry discoveryRegistry;
    private final Clock clock;
    private final ConcurrentMap<CacheKey, CacheEntry> cache =
            new ConcurrentHashMap<>();

    @Autowired
    public ProviderModelDiscoveryService(
            IAiProviderConnectionService connectionService,
            ProviderProfileValueValidator profileValidator,
            ProtocolAdapterRegistry protocolRegistry,
            RemoteModelDiscoveryRegistry discoveryRegistry) {
        this(connectionService, profileValidator, protocolRegistry,
                discoveryRegistry, Clock.systemUTC());
    }

    ProviderModelDiscoveryService(
            IAiProviderConnectionService connectionService,
            ProviderProfileValueValidator profileValidator,
            ProtocolAdapterRegistry protocolRegistry,
            RemoteModelDiscoveryRegistry discoveryRegistry,
            Clock clock) {
        this.connectionService = connectionService;
        this.profileValidator = profileValidator;
        this.protocolRegistry = protocolRegistry;
        this.discoveryRegistry = discoveryRegistry;
        this.clock = clock;
    }

    public List<RemoteModelInfo> listModels(Long connectionId) {
        ProviderConnectionRuntime connection = connectionService.getRuntime(connectionId);
        SchemaProfileDefinition profile = profileValidator.requireProvider(
                connection.providerCode(), connection.protocolCode());
        if (!profile.modelDiscoverySupported()) {
            throw new ServiceException("MODEL_DISCOVERY_NOT_SUPPORTED");
        }
        CacheKey key = new CacheKey(
                connection.id(), connection.revision(), "MODELS");
        Instant now = clock.instant();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.models();
        }

        ProtocolAdapter protocol = protocolRegistry.getRequired(
                connection.protocolCode());
        ProtocolEndpoint endpoint = protocol.modelDiscoveryEndpoint()
                .orElseThrow(() -> new ServiceException(
                        "MODEL_DISCOVERY_NOT_SUPPORTED"));
        List<RemoteModelInfo> models = List.copyOf(
                discoveryRegistry.getRequired(connection.protocolCode())
                        .listModels(connection, endpoint));
        prune(now);
        cache.put(key, new CacheEntry(models, now.plus(CACHE_TTL)));
        return models;
    }

    private void prune(Instant now) {
        cache.entrySet().removeIf(entry ->
                !entry.getValue().expiresAt().isAfter(now));
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
    }

    record CacheKey(
            Long connectionId,
            long connectionRevision,
            String discoveryType) {
    }

    private record CacheEntry(
            List<RemoteModelInfo> models,
            Instant expiresAt) {
    }
}
