package com.polaris.ai.workflow.registry;

import com.polaris.ai.workflow.spi.WorkflowNodeSchemaResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** 动态节点 Schema 解析器注册表。 */
@Component
public class WorkflowNodeSchemaRegistry {

    private final List<WorkflowNodeSchemaResolver> resolvers;

    public WorkflowNodeSchemaRegistry(
            @Autowired(required = false) List<WorkflowNodeSchemaResolver> resolvers) {
        this.resolvers = resolvers == null ? List.of() : List.copyOf(resolvers);
    }

    public Optional<WorkflowNodeSchemaResolver> find(String nodeType, String handlerVersion) {
        List<WorkflowNodeSchemaResolver> matched = resolvers.stream()
                .filter(resolver -> resolver.supports(nodeType, handlerVersion))
                .toList();
        if (matched.size() > 1) {
            throw new IllegalStateException(
                    "工作流节点 Schema 解析器重复匹配: " + nodeType + ":" + handlerVersion);
        }
        return matched.stream().findFirst();
    }
}
