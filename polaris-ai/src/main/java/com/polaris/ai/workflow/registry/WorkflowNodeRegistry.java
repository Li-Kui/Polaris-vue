package com.polaris.ai.workflow.registry;

import com.polaris.ai.workflow.spi.WorkflowNodeDescriptor;
import com.polaris.ai.workflow.spi.WorkflowNodeDescriptorResolver;
import com.polaris.ai.workflow.spi.WorkflowNodeHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/** 感知版本的工作流节点插件注册表。 */
@Component
public class WorkflowNodeRegistry implements WorkflowNodeDescriptorResolver {

    private final Map<String, WorkflowNodeHandler> handlers;
    private final List<WorkflowNodeDescriptor> descriptors;

    public WorkflowNodeRegistry(@Autowired(required = false) List<WorkflowNodeHandler> handlers) {
        Map<String, WorkflowNodeHandler> registered = new LinkedHashMap<>();
        for (WorkflowNodeHandler handler : handlers == null ? List.<WorkflowNodeHandler>of() : handlers) {
            if (handler == null || handler.descriptor() == null) {
                throw new IllegalStateException("工作流节点处理器及描述不能为空");
            }
            WorkflowNodeDescriptor descriptor = handler.descriptor();
            String key = key(descriptor.type(), descriptor.handlerVersion());
            WorkflowNodeHandler previous = registered.putIfAbsent(key, handler);
            if (previous != null) {
                throw new IllegalStateException("工作流节点处理器重复注册: " + key);
            }
        }
        this.handlers = Map.copyOf(registered);
        this.descriptors = registered.values().stream()
                .map(WorkflowNodeHandler::descriptor)
                .sorted(Comparator.comparing(WorkflowNodeDescriptor::type)
                        .thenComparing(WorkflowNodeDescriptor::handlerVersion))
                .toList();
    }

    @Override
    public Optional<WorkflowNodeDescriptor> find(String type, String handlerVersion) {
        WorkflowNodeHandler handler = handlers.get(key(type, handlerVersion));
        return handler == null ? Optional.empty() : Optional.of(handler.descriptor());
    }

    @Override
    public Collection<WorkflowNodeDescriptor> list() {
        return new ArrayList<>(descriptors);
    }

    public Optional<WorkflowNodeHandler> findHandler(String type, String handlerVersion) {
        return Optional.ofNullable(handlers.get(key(type, handlerVersion)));
    }

    private String key(String type, String handlerVersion) {
        return String.valueOf(type) + ":" + String.valueOf(handlerVersion);
    }
}
