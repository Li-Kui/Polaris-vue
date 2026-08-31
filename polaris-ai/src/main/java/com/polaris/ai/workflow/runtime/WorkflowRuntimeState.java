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
    private Map<String, Integer> runCounts = new LinkedHashMap<>();
    private Map<String, Integer> loopCounts = new LinkedHashMap<>();
    private Map<String, Integer> arrivals = new LinkedHashMap<>();
    private Map<String, BigDecimal> usage = new LinkedHashMap<>();
    private JsonNode approval;
    private Set<String> completedKeys = new LinkedHashSet<>();
    private List<CompensationCandidate> compensations = new ArrayList<>();
    private Set<String> compensatedKeys = new LinkedHashSet<>();
    private int totalNodeRuns;
    private boolean reachedEnd;

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
