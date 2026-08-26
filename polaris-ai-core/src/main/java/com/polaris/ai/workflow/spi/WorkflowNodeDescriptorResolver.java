package com.polaris.ai.workflow.spi;

import java.util.Collection;
import java.util.Optional;

/** 编译器和 API 层使用的只读节点描述注册表契约。 */
public interface WorkflowNodeDescriptorResolver {

    Optional<WorkflowNodeDescriptor> find(String type, String handlerVersion);

    Collection<WorkflowNodeDescriptor> list();
}
