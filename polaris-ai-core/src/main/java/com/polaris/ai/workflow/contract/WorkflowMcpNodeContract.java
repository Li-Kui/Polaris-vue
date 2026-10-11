package com.polaris.ai.workflow.contract;

import com.polaris.ai.workflow.application.WorkflowResolvedNodeSchemaView;
import com.polaris.ai.workflow.definition.WorkflowDiagnostic;
import com.polaris.ai.workflow.definition.WorkflowExecutionPlan;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/** MCP 节点跨模块契约；只保存本地工具身份与审批快照，不包含端点和凭证。 */
public final class WorkflowMcpNodeContract {
    public static final String TYPE = "mcp_tool";
    public static final String VERSION = "1.0";
    public static final String RESOURCE_KIND = "MCP_TOOL";
    private static final List<String> SNAPSHOT_FIELDS = List.of(
            "toolId", "revision", "connectionRevision", "catalogRevision", "structureHash");

    private WorkflowMcpNodeContract() {}

    public static String snapshotVersion(JsonNode config) {
        if (config == null || !config.isObject()) throw new IllegalArgumentException("MCP工具快照尚未确认");
        String id = config.path("toolId").asString();
        String hash = config.path("structureHash").asString();
        if (!config.path("toolId").isString() || !config.path("structureHash").isString() || !id.matches("[1-9][0-9]{0,18}")
                || !hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("MCP工具快照格式无效");
        List<String> values = new ArrayList<>();
        values.add(id);
        for (String field : List.of("revision", "connectionRevision", "catalogRevision")) {
            JsonNode value = config.path(field);
            if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 1) {
                throw new IllegalArgumentException("MCP工具快照版本无效");
            }
            values.add(String.valueOf(value.asLong()));
        }
        values.add(hash);
        return String.join(":", values);
    }

    public static ObjectNode snapshot(String toolId, long revision, long connectionRevision,
            long catalogRevision, String structureHash) {
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        config.put("toolId", toolId);
        config.put("revision", revision);
        config.put("connectionRevision", connectionRevision);
        config.put("catalogRevision", catalogRevision);
        config.put("structureHash", structureHash);
        snapshotVersion(config);
        return config;
    }

    public static ObjectNode configSchema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("toolId").put("type", "string").put("pattern", "^[1-9][0-9]{0,18}$");
        for (String field : List.of("revision", "connectionRevision", "catalogRevision")) {
            properties.putObject(field).put("type", "integer").put("minimum", 1);
        }
        properties.putObject("structureHash").put("type", "string").put("pattern", "^[0-9a-f]{64}$");
        SNAPSHOT_FIELDS.forEach(schema.putArray("required")::add);
        return schema;
    }

    public static ObjectNode outputSchema(JsonNode structuredSchema) {
        ObjectNode schema = objectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("invocationId").put("type", "string").put("title", "MCP 调用 ID");
        properties.putObject("status").put("type", "string");
        properties.putObject("durationMs").put("type", "integer");
        properties.putObject("auditRecorded").put("type", "boolean");
        properties.putObject("content").put("type", "array").putObject("items")
                .put("type", "object").put("additionalProperties", true);
        if (structuredSchema != null) properties.set("structuredContent", structuredSchema.deepCopy());
        else properties.putObject("structuredContent").put("type", "object").put("additionalProperties", true);
        schema.putArray("required").add("invocationId").add("status").add("durationMs").add("auditRecorded").add("content");
        return schema;
    }

    public static ObjectNode objectSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        schema.put("additionalProperties", false);
        return schema;
    }

    /** 动态资源解析失败不得以通用 Schema 的警告形式继续发布。 */
    public static List<WorkflowDiagnostic> validatePublication(WorkflowExecutionPlan plan,
            List<WorkflowResolvedNodeSchemaView> schemas) {
        List<WorkflowDiagnostic> errors = new ArrayList<>();
        for (var node : plan.getNodes()) {
            if (!TYPE.equals(node.getType())) continue;
            var matches = (schemas == null ? List.<WorkflowResolvedNodeSchemaView>of() : schemas).stream()
                    .filter(item -> item != null && node.getId().equals(item.nodeId())).toList();
            boolean valid = false;
            if (matches.size() == 1) {
                var schema = matches.get(0);
                try {
                    valid = TYPE.equals(schema.nodeType()) && VERSION.equals(schema.handlerVersion())
                            && RESOURCE_KIND.equals(schema.source())
                            && snapshotVersion(node.getConfig()).equals(schema.sourceVersion())
                            && schema.inputSchema() != null && schema.outputSchema() != null
                            && (schema.diagnostics() == null || schema.diagnostics().isEmpty());
                } catch (IllegalArgumentException ignored) { /* 不回显远端内容或草稿配置。 */ }
            }
            if (!valid) errors.add(WorkflowDiagnostic.error("MCP_TOOL_SNAPSHOT_INVALID", node.getId(),
                    "$.nodes." + node.getId(), "MCP工具不可用或版本快照已变化，请重新选择当前已审批工具后发布"));
        }
        return List.copyOf(errors);
    }
}
