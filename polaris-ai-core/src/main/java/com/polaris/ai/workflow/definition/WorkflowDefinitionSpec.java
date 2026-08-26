package com.polaris.ai.workflow.definition;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 映射为 JSON 的工作流定义契约。 */
@Data
public class WorkflowDefinitionSpec {
    private String schemaVersion;
    private Metadata metadata;
    private JsonNode inputs;
    private List<Node> nodes = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();
    private Map<String, ValueBinding> outputs = new LinkedHashMap<>();
    private Policies policies;
    private JsonNode ui;

    @Data
    public static class Metadata {
        private String code;
        private String name;
        private String description;
        private List<String> tags = new ArrayList<>();
    }

    @Data
    public static class Node {
        private String id;
        private String type;
        private String typeVersion;
        private String name;
        private Map<String, ValueBinding> inputMapping = new LinkedHashMap<>();
        private JsonNode config;
        private RetryPolicy retryPolicy;
        private Integer timeoutSeconds;
        private String onError;
        private String compensationNodeId;
        private List<ResourceRef> resourceRefs = new ArrayList<>();
        private JsonNode ui;
    }

    @Data
    public static class Edge {
        private String id;
        private String source;
        private String sourcePort;
        private String target;
        private String targetPort;
        private String kind;
        private Condition condition;
        private Boolean defaultEdge;

        @com.fasterxml.jackson.annotation.JsonProperty("default")
        public Boolean getDefaultEdge() {
            return defaultEdge;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("default")
        public void setDefaultEdge(Boolean defaultEdge) {
            this.defaultEdge = defaultEdge;
        }
    }

    @Data
    public static class Condition {
        private String expression;
        private Integer priority;
        private String onError;
    }

    @Data
    public static class ValueBinding {
        private String expression;
        private JsonNode value;
    }

    @Data
    public static class RetryPolicy {
        private Integer maxAttempts;
        private String backoff;
        private Long initialDelayMs;
        private Long maxDelayMs;
        private List<String> retryableErrors = new ArrayList<>();
    }

    @Data
    public static class ResourceRef {
        private String kind;
        private String key;
        private Boolean required;
    }

    @Data
    public static class Policies {
        private Integer timeoutSeconds;
        private Integer maxNodeRuns;
        private Integer maxParallelism;
        private Long tokenBudget;
        private BigDecimal costBudget;
    }
}
