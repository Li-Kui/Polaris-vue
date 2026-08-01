package com.polaris.ai.workflow.langgraph;

import lombok.Data;

import java.util.*;

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
        if (nodes.size() > 200) {
            throw new IllegalArgumentException("工作流节点数量不能超过 200");
        }
        if (edges == null || edges.isEmpty()) {
            throw new IllegalArgumentException("graphJson 中 edges 不能为空");
        }
        if (edges.size() > 1000) {
            throw new IllegalArgumentException("工作流连线数量不能超过 1000");
        }
        if (maxIterations < 1 || maxIterations > 1000) {
            throw new IllegalArgumentException("maxIterations 必须在 1 到 1000 之间");
        }

        Map<String, NodeDef> nodesById = new LinkedHashMap<>();
        for (NodeDef node : nodes) {
            String id = trim(node.getId());
            if (id == null) {
                throw new IllegalArgumentException("节点 id 不能为空");
            }
            if (id.length() > 128 || !id.matches("[A-Za-z0-9_.-]+")) {
                throw new IllegalArgumentException(
                        "节点 id 仅允许字母、数字、下划线、点和短横线，且不能超过 128 个字符: " + id);
            }
            if ("__start__".equals(id) || "__end__".equals(id)) {
                throw new IllegalArgumentException("节点 id 不能使用系统保留值: " + id);
            }
            if (nodesById.putIfAbsent(id, node) != null) {
                throw new IllegalArgumentException("节点 id 重复: " + id);
            }
            node.setId(id);

            String type = trim(node.getType());
            if (!Set.of("agent", "java", "classifier").contains(type)) {
                throw new IllegalArgumentException("节点 [" + id + "] 类型无效: " + node.getType());
            }
            node.setType(type);
            if (("agent".equals(type) || "java".equals(type)) && trim(node.getRef()) == null) {
                throw new IllegalArgumentException("节点 [" + id + "] 缺少引用 ref");
            }
            if (node.getTimeoutSeconds() < 1 || node.getTimeoutSeconds() > 1800) {
                throw new IllegalArgumentException(
                        "节点 [" + id + "] timeoutSeconds 必须在 1 到 1800 之间");
            }
        }

        Map<String, List<EdgeDef>> outgoing = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, List<String>> reverse = new HashMap<>();
        Set<String> edgeKeys = new HashSet<>();
        Set<String> allIds = new HashSet<>(nodesById.keySet());
        allIds.add("__start__");
        allIds.add("__end__");
        for (EdgeDef edge : edges) {
            String from = trim(edge.getFrom());
            String to = trim(edge.getTo());
            String condition = trim(edge.getCondition());
            if (!allIds.contains(from)) {
                throw new IllegalArgumentException("边引用了不存在的源节点: " + edge.getFrom());
            }
            if (!allIds.contains(to)) {
                throw new IllegalArgumentException("边引用了不存在的目标节点: " + edge.getTo());
            }
            if ("__end__".equals(from)) {
                throw new IllegalArgumentException("__end__ 不能存在出边");
            }
            if ("__start__".equals(to)) {
                throw new IllegalArgumentException("__start__ 不能存在入边");
            }
            if (condition != null && condition.length() > 64) {
                throw new IllegalArgumentException("边 condition 长度不能超过 64");
            }
            String edgeKey = from + "\u0000" + to + "\u0000" + Objects.toString(condition, "");
            if (!edgeKeys.add(edgeKey)) {
                throw new IllegalArgumentException("存在重复连线: " + from + " -> " + to);
            }
            edge.setFrom(from);
            edge.setTo(to);
            edge.setCondition(condition);
            outgoing.computeIfAbsent(from, ignored -> new ArrayList<>()).add(edge);
            adjacency.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
            reverse.computeIfAbsent(to, ignored -> new ArrayList<>()).add(from);
        }

        List<EdgeDef> startEdges = outgoing.getOrDefault("__start__", List.of());
        if (startEdges.size() != 1 || trim(startEdges.get(0).getCondition()) != null) {
            throw new IllegalArgumentException("工作流必须且只能有一条从 __start__ 出发的无条件连线");
        }
        if (!reverse.containsKey("__end__")) {
            throw new IllegalArgumentException("工作流缺少指向 __end__ 的连线");
        }

        for (Map.Entry<String, NodeDef> entry : nodesById.entrySet()) {
            validateOutgoing(entry.getKey(), entry.getValue(),
                    outgoing.getOrDefault(entry.getKey(), List.of()));
        }

        Set<String> reachable = traverse("__start__", adjacency);
        Set<String> canReachEnd = traverse("__end__", reverse);
        for (String nodeId : nodesById.keySet()) {
            if (!reachable.contains(nodeId)) {
                throw new IllegalArgumentException("节点从 __start__ 不可达: " + nodeId);
            }
            if (!canReachEnd.contains(nodeId)) {
                throw new IllegalArgumentException("节点无法到达 __end__: " + nodeId);
            }
        }

        // 普通边禁止成环；循环必须经过分类节点的条件边并受 maxIterations 保护。
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

    }

    private void validateOutgoing(String nodeId, NodeDef node, List<EdgeDef> outgoing) {
        if (!"classifier".equals(node.getType())) {
            if (outgoing.size() != 1 || trim(outgoing.get(0).getCondition()) != null) {
                throw new IllegalArgumentException(
                        "非分类节点 [" + nodeId + "] 必须且只能有一条无条件出边");
            }
            return;
        }

        if (node.getBranches() == null || node.getBranches().isEmpty()) {
            throw new IllegalArgumentException("分类节点 [" + nodeId + "] 未定义任何分支出口");
        }
        Set<String> slugs = new LinkedHashSet<>();
        for (BranchDef branch : node.getBranches()) {
            String slug = trim(branch.getSlug());
            if (slug == null || !slug.matches("[A-Za-z0-9_-]{1,64}")) {
                throw new IllegalArgumentException("分类节点 [" + nodeId + "] 的分支 slug 格式无效");
            }
            if (!slugs.add(slug)) {
                throw new IllegalArgumentException("分类节点 [" + nodeId + "] 的分支 slug 重复: " + slug);
            }
            if (trim(branch.getLabel()) == null || branch.getLabel().length() > 500) {
                throw new IllegalArgumentException(
                        "分类节点 [" + nodeId + "] 的分支说明不能为空且不能超过 500 字符");
            }
            branch.setSlug(slug);
        }

        Set<String> edgeConditions = new HashSet<>();
        int defaultCount = 0;
        for (EdgeDef edge : outgoing) {
            String condition = trim(edge.getCondition());
            if (condition == null) {
                defaultCount++;
            } else if (!slugs.contains(condition)) {
                throw new IllegalArgumentException(
                        "分类节点 [" + nodeId + "] 的连线条件 '" + condition + "' 不属于已定义的分支");
            } else if (!edgeConditions.add(condition)) {
                throw new IllegalArgumentException(
                        "分类节点 [" + nodeId + "] 的分支连线重复: " + condition);
            }
        }
        if (defaultCount > 1) {
            throw new IllegalArgumentException("分类节点 [" + nodeId + "] 最多只能有一条默认出边");
        }
        if (!edgeConditions.equals(slugs)) {
            Set<String> missing = new LinkedHashSet<>(slugs);
            missing.removeAll(edgeConditions);
            throw new IllegalArgumentException(
                    "分类节点 [" + nodeId + "] 存在未连线分支: " + String.join(",", missing));
        }
    }

    private Set<String> traverse(String start, Map<String, List<String>> graph) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            for (String next : graph.getOrDefault(current, List.of())) {
                queue.addLast(next);
            }
        }
        return visited;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
