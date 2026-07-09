package com.polaris.ai.workflow.langgraph;

import lombok.Data;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * graphJson 解析后的拓扑结构 DTO
 *
 * @author polaris
 */
@Data
public class GraphTopology {

    /** 节点定义列表 */
    private List<NodeDef> nodes;

    /** 边定义列表 */
    private List<EdgeDef> edges;

    /** 最大循环次数（防死循环，默认 10） */
    private int maxIterations = 10;

    /**
     * 校验拓扑合法性
     *
     * @throws IllegalArgumentException 校验失败时抛出
     */
    public void validate() {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("graphJson 中 nodes 不能为空");
        }
        if (edges == null || edges.isEmpty()) {
            throw new IllegalArgumentException("graphJson 中 edges 不能为空");
        }

        Set<String> nodeIds = nodes.stream()
                .map(NodeDef::getId)
                .collect(Collectors.toSet());
        nodeIds.add("__start__");
        nodeIds.add("__end__");

        for (EdgeDef edge : edges) {
            if (!nodeIds.contains(edge.getFrom())) {
                throw new IllegalArgumentException("边引用了不存在的源节点: " + edge.getFrom());
            }
            if (!nodeIds.contains(edge.getTo())) {
                throw new IllegalArgumentException("边引用了不存在的目标节点: " + edge.getTo());
            }
        }

        boolean hasStart = edges.stream().anyMatch(e -> "__start__".equals(e.getFrom()));
        if (!hasStart) {
            throw new IllegalArgumentException("graphJson 缺少从 __start__ 出发的边");
        }
    }

    /**
     * 判断是否包含条件边
     */
    public boolean hasConditionalEdges() {
        return edges != null && edges.stream().anyMatch(e -> e.getCondition() != null && !e.getCondition().isEmpty());
    }

    /**
     * 节点定义
     */
    @Data
    public static class NodeDef {
        /** 节点唯一标识 */
        private String id;
        /** 节点类型：agent（AI智能体）/ java（WorkflowNodeExecutor） */
        private String type;
        /** 引用编码：AiAgent.agentCode 或 WorkflowNodeExecutor.getNodeCode() */
        private String ref;
        /** 节点执行超时（秒），默认 120 */
        private int timeoutSeconds = 120;
        /** 是否需要审批中断（Human-in-the-Loop），默认 false */
        private boolean requireApproval = false;
    }

    /**
     * 边定义
     */
    @Data
    public static class EdgeDef {
        /** 源节点ID */
        private String from;
        /** 目标节点ID */
        private String to;
        /** 条件值（仅条件边使用，普通边为 null） */
        private String condition;
    }
}
