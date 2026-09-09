package com.polaris.ai.workflow.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;
import java.util.*;

/** 保存在每个安全检查点中的可序列化调度状态。 */
@Data
public class WorkflowRuntimeState {
    private JsonNode input;
    private List<Token> pending = new ArrayList<>();
    private Map<String, JsonNode> outputs = new LinkedHashMap<>();
    /** 每个节点实例的输出。分支路径包含循环层级，避免不同迭代互相覆盖。 */
    private Map<String, JsonNode> instanceOutputs = new LinkedHashMap<>();
    private Map<String, Integer> runCounts = new LinkedHashMap<>();
    private Map<String, Integer> loopCounts = new LinkedHashMap<>();
    /** V2 循环按“节点 + 外层分支路径”隔离，支持嵌套和断点恢复。 */
    private Map<String, LoopState> loops = new LinkedHashMap<>();
    private Map<String, Integer> arrivals = new LinkedHashMap<>();
    private Map<String, BigDecimal> usage = new LinkedHashMap<>();
    private JsonNode approval;
    private Set<String> completedKeys = new LinkedHashSet<>();
    private List<CompensationCandidate> compensations = new ArrayList<>();
    private Set<String> compensatedKeys = new LinkedHashSet<>();
    private int totalNodeRuns;
    private boolean reachedEnd;

    @Data
    public static class LoopState {
        private String nodeId;
        private String instanceKey;
        private String parentBranchPath;
        private String currentBranchPath;
        private String mode;
        private String repeatMode;
        private JsonNode items;
        private JsonNode currentItem;
        private JsonNode lastOutput;
        private List<JsonNode> results = new ArrayList<>();
        private List<JsonNode> errors = new ArrayList<>();
        /** 失败轮次数；SKIP 不保留错误详情，但仍计入失败统计。 */
        private int failureCount;
        private int iteration;
        private int cursor;
        private int total;
        private boolean completed;
        private String stopReason;
    }

    @Data
    public static class Token {
        private String nodeId;
        private String branchPath;

        public Token() {
        }

        public Token(String nodeId, String branchPath) {
            this.nodeId = nodeId;
            this.branchPath = branchPath;
        }
    }

    @Data
    public static class CompensationCandidate {
        private String nodeId;
        private String branchPath;
        private String compensationNodeId;

        public CompensationCandidate() {
        }

        public CompensationCandidate(
                String nodeId, String branchPath, String compensationNodeId) {
            this.nodeId = nodeId;
            this.branchPath = branchPath;
            this.compensationNodeId = compensationNodeId;
        }

        public String key() {
            return nodeId + "@" + branchPath;
        }
    }
}
