package com.polaris.ai.workflow.definition;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 由工作流草稿确定性编译生成的可执行计划。 */
@Data
public class WorkflowExecutionPlan {
    private String planSchemaVersion;
    private String definitionSchemaVersion;
    private String workflowVersionId;
    private String contentHash;
    private JsonNode inputs;
    private List<String> entryNodeIds = new ArrayList<>();
    private List<PlanNode> nodes = new ArrayList<>();
    private List<PlanEdge> edges = new ArrayList<>();
    private Map<String, PlanValueBinding> outputs = new LinkedHashMap<>();
    private Map<String, Object> policies = new LinkedHashMap<>();

    @Data
    public static class PlanNode {
        private String id;
        private String type;
        private String handlerVersion;
        private Map<String, PlanValueBinding> inputMapping = new LinkedHashMap<>();
        private JsonNode config;
        private String sideEffect;
        private JsonNode inputSchema;
        private JsonNode outputSchema;
        private String schemaSource;
        private String schemaSourceVersion;
        private String outputSchemaBaseHash;
        private String onError;
        private String compensationNodeId;
        private Integer timeoutSeconds;
        private WorkflowDefinitionSpec.RetryPolicy retryPolicy;
        private List<WorkflowDefinitionSpec.ResourceRef> resourceRefs = new ArrayList<>();
    }

    @Data
    public static class PlanEdge {
        private String source;
        private String sourcePort;
        private String target;
        private String kind;
        private JsonNode expressionAst;
        private Integer priority;
        private Boolean defaultEdge;
        private String conditionOnError;

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
    public static class PlanValueBinding {
        private JsonNode expressionAst;
        private JsonNode value;
    }
}
