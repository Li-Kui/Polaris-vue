package com.polaris.ai.workflow.langgraph;

/** 节点超过拓扑定义的硬超时。 */
public class NodeTimeoutException extends RuntimeException {

    private final String nodeId;

    public NodeTimeoutException(String nodeId, int timeoutSeconds) {
        super("节点 " + nodeId + " 执行超时(" + timeoutSeconds + "s)");
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }
}
