package com.polaris.ai.workflow.registry;

import com.polaris.ai.workflow.spi.WorkflowResourceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/** 租户感知工作流资源提供器注册表。 */
@Component
public class WorkflowResourceRegistry {

    private final Map<String, WorkflowResourceProvider> providers;

    public WorkflowResourceRegistry(
            @Autowired(required = false) List<WorkflowResourceProvider> providers) {
        Map<String, WorkflowResourceProvider> registered = new LinkedHashMap<>();
        for (WorkflowResourceProvider provider
                : providers == null ? List.<WorkflowResourceProvider>of() : providers) {
            String kind = normalize(provider.kind());
            if (kind == null) {
                throw new IllegalStateException("工作流资源提供器类型不能为空");
            }
            if (registered.putIfAbsent(kind, provider) != null) {
                throw new IllegalStateException("工作流资源提供器重复注册: " + kind);
            }
        }
        this.providers = Map.copyOf(registered);
    }

    public Optional<WorkflowResourceProvider> find(String kind) {
        return Optional.ofNullable(providers.get(normalize(kind)));
    }

    public Collection<WorkflowResourceProvider> list() {
        return providers.values();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
