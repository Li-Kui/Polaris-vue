package com.polaris.ai.workflow.langgraph;

import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * graphJson 解析后的拓扑结构 DTO
 *
 * @author polaris
 */
@Data
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
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
                .map(n -> n.getId() != null && !n.getId().isEmpty() ? n.getId() : n.getRef())
                .filter(java.util.Objects::nonNull)
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

        // Kahn 算法：针对无条件自动流转边（不带 condition）做环路死循环检测
        Map<String, Integer> inDegrees = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();

        for (EdgeDef edge : edges) {
            String cond = edge.getCondition();
            if (cond == null || cond.trim().isEmpty()) {
                String u = edge.getFrom();
                String v = edge.getTo();
                adj.computeIfAbsent(u, k -> new ArrayList<>()).add(v);
                inDegrees.put(v, inDegrees.getOrDefault(v, 0) + 1);
                if (!inDegrees.containsKey(u)) {
                    inDegrees.put(u, 0);
                }
            }
        }

        if (!inDegrees.isEmpty()) {
            Queue<String> queue = new LinkedList<>();
            for (Map.Entry<String, Integer> entry : inDegrees.entrySet()) {
                if (entry.getValue() == 0) {
                    queue.offer(entry.getKey());
                }
            }

            int visitedCount = 0;
            while (!queue.isEmpty()) {
                String u = queue.poll();
                visitedCount++;
                List<String> neighbors = adj.get(u);
                if (neighbors != null) {
                    for (String v : neighbors) {
                        inDegrees.put(v, inDegrees.get(v) - 1);
                        if (inDegrees.get(v) == 0) {
                            queue.offer(v);
                        }
                    }
                }
            }

            if (visitedCount < inDegrees.size()) {
                throw new IllegalArgumentException("工作流中存在由普通顺序连线构成的环路，会导致无条件死循环，请检查连线配置");
            }
        }

        // 分类节点校验：每个 classifier 节点必须定义分支，且其出边的 condition 必须命中已定义的 slug
        for (NodeDef node : nodes) {
            if (!"classifier".equals(node.getType())) {
                continue;
            }
            String nodeId = node.getId() != null && !node.getId().isEmpty() ? node.getId() : node.getRef();
            if (node.getBranches() == null || node.getBranches().isEmpty()) {
                throw new IllegalArgumentException("分类节点 [" + nodeId + "] 未定义任何分支出口");
            }
            Set<String> validSlugs = node.getBranches().stream()
                    .map(BranchDef::getSlug)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());

            for (EdgeDef edge : edges) {
                if (!nodeId.equals(edge.getFrom())) {
                    continue;
                }
                String cond = edge.getCondition();
                // 条件边的 condition 必须是已定义的分支 slug（默认兜底边 condition 为空，跳过）
                if (cond != null && !cond.trim().isEmpty() && !validSlugs.contains(cond.trim())) {
                    throw new IllegalArgumentException(
                            "分类节点 [" + nodeId + "] 的连线条件 '" + cond + "' 不属于已定义的分支，请重新配置");
                }
            }
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
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class NodeDef {
        /** 节点唯一标识 */
        private String id;
        /** 节点类型：agent（AI智能体）/ java（WorkflowNodeExecutor）/ classifier（意图分类路由） */
        private String type;
        /** 引用编码：AiAgent.agentCode 或 WorkflowNodeExecutor.getNodeCode() */
        private String ref;
        /** 节点执行超时（秒），默认 120 */
        private int timeoutSeconds = 120;
        /** 是否需要审批中断（Human-in-the-Loop），默认 false */
        private boolean requireApproval = false;
        /** 分类节点的分支出口定义（仅 type=classifier 使用） */
        private List<BranchDef> branches;
        /** 分类节点使用的模型配置ID（仅 type=classifier 使用，为空则用系统默认模型） */
        private Long modelConfigId;
    }

    /**
     * 分类节点的分支出口定义
     */
    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class BranchDef {
        /** 稳定标识（前端生成，永不变，用作边的 condition 值） */
        private String slug;
        /** 分支的中文业务描述 */
        private String label;
    }

    /**
     * 边定义
     */
    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class EdgeDef {
        /** 源节点ID */
        private String from;
        /** 目标节点ID */
        private String to;
        /** 条件值（仅条件边使用，普通边为 null） */
        private String condition;
    }
}
