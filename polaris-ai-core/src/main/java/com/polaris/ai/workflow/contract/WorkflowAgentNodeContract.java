package com.polaris.ai.workflow.contract;

import com.polaris.ai.workflow.application.WorkflowResolvedNodeSchemaView;
import com.polaris.ai.workflow.definition.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.*;
import java.util.*;

/** 唯一当前 Agent 契约：配置冻结版本，问题和外发查询独立映射。 */
public final class WorkflowAgentNodeContract {
    public static final String TYPE = "agent", VERSION = "2.0", SOURCE = "AGENT_COLLABORATION";
    private WorkflowAgentNodeContract() { }
    public static String snapshotVersion(JsonNode config) {
        if (config == null || !config.isObject() || !VERSION.equals(config.path("configVersion").asString())
                || !config.path("agentId").isString() || !config.path("agentId").asString().matches("[1-9][0-9]{0,18}")
                || !config.path("definitionHash").asString().matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Agent快照未确认");
        boolean knowledge = config.has("knowledgeId") || config.has("knowledgeHash");
        if (knowledge && (!config.path("knowledgeId").isString() || !config.path("knowledgeId").asString().matches("[1-9][0-9]{0,18}")
                || !config.path("knowledgeHash").asString().matches("[a-f0-9]{64}"))) throw new IllegalArgumentException("知识快照未确认");
        return config.path("agentId").asString() + ":" + config.path("definitionHash").asString()
                + (knowledge ? ":" + config.path("knowledgeId").asString() + ":" + config.path("knowledgeHash").asString() : "");
    }
    public static ObjectNode object() {
        ObjectNode node = JsonNodeFactory.instance.objectNode(); node.put("type", "object");
        node.putObject("properties"); node.put("additionalProperties", false); return node;
    }
    public static ObjectNode string(String title, int maximum) {
        ObjectNode node = JsonNodeFactory.instance.objectNode(); node.put("type", "string"); node.put("title", title);
        if (maximum > 0) node.put("maxLength", maximum); return node;
    }
    public static ObjectNode configSchema() {
        ObjectNode schema = object(), props = (ObjectNode) schema.get("properties");
        props.set("configVersion", string("配置版本", 0).put("const", VERSION).put("default", VERSION));
        props.set("agentId", string("智能体 ID", 0).put("pattern", "^[1-9][0-9]{0,18}$"));
        props.set("definitionHash", string("智能体版本摘要", 0).put("pattern", "^[a-f0-9]{64}$"));
        props.set("knowledgeId", string("知识库 ID", 0).put("pattern", "^[1-9][0-9]{0,18}$"));
        props.set("knowledgeHash", string("知识库版本摘要", 0).put("pattern", "^[a-f0-9]{64}$"));
        props.putObject("maxCalls").put("type", "integer").put("minimum", 0).put("maximum", 2).put("default", 2).put("title", "查询次数上限");
        props.putObject("maxWaitSeconds").put("type", "integer").put("minimum", 1).put("maximum", 180).put("default", 90).put("title", "总时限（秒）");
        schema.putArray("required").add("configVersion").add("agentId").add("definitionHash").add("maxCalls").add("maxWaitSeconds");
        return schema;
    }
    public static ObjectNode inputSchema(boolean tools) {
        ObjectNode schema = object(), props = (ObjectNode) schema.get("properties");
        props.set("question", string("本次问题", 4000).put("minLength", 1));
        props.set("externalQuery", string("允许外发的查询文本", 4000));
        schema.putArray("required").add("question"); if (tools) schema.withArray("required").add("externalQuery");
        return schema;
    }
    public static ObjectNode outputSchema() {
        ObjectNode schema = object(), props = (ObjectNode) schema.get("properties");
        props.set("text", string("回答", 16000)); props.set("agentRunId", string("协作运行 ID", 64));
        props.set("agentId", string("智能体 ID", 64));
        props.putObject("knowledgeCitations").put("type", "array").set("items", string("知识引用", 16));
        for (String name : List.of("knowledgeSources", "toolSources")) {
            ObjectNode item = object(); item.put("additionalProperties", true);
            props.putObject(name).put("type", "array").set("items", item);
        }
        schema.putArray("required").add("text").add("agentRunId").add("agentId").add("knowledgeCitations").add("knowledgeSources").add("toolSources");
        return schema;
    }
    public static List<WorkflowDiagnostic> validatePublication(WorkflowExecutionPlan plan, List<WorkflowResolvedNodeSchemaView> schemas) {
        List<WorkflowDiagnostic> errors = new ArrayList<>();
        for (var node : plan.getNodes()) {
            if (!TYPE.equals(node.getType())) continue;
            var matches = (schemas == null ? List.<WorkflowResolvedNodeSchemaView>of() : schemas).stream()
                    .filter(schema -> node.getId().equals(schema.nodeId())).toList();
            boolean valid = false;
            try {
                valid = VERSION.equals(node.getHandlerVersion()) && matches.size() == 1
                        && SOURCE.equals(matches.get(0).source()) && VERSION.equals(matches.get(0).handlerVersion())
                        && matches.get(0).inputSchema() != null && matches.get(0).outputSchema() != null
                        && snapshotVersion(node.getConfig()).equals(matches.get(0).sourceVersion())
                        && (matches.get(0).diagnostics() == null || matches.get(0).diagnostics().isEmpty());
            } catch (RuntimeException ignored) { }
            if (!valid) errors.add(WorkflowDiagnostic.error("AGENT_SNAPSHOT_INVALID", node.getId(), "$.nodes." + node.getId(),
                    "协作智能体或知识快照无效，请重新确认当前版本后发布"));
            if (valid) for (JsonNode required : matches.get(0).inputSchema().path("required")) {
                String key = required.asString();
                if (!node.getInputMapping().containsKey(key)) errors.add(WorkflowDiagnostic.error("AGENT_INPUT_MAPPING_REQUIRED", node.getId(),
                        "$.nodes." + node.getId() + ".inputMapping." + key, "请显式映射协作输入：" + key));
            }
        }
        return List.copyOf(errors);
    }
}
