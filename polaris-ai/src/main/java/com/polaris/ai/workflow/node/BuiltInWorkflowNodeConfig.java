package com.polaris.ai.workflow.node;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.workflow.application.WorkflowArtifactView;
import com.polaris.ai.workflow.service.WorkflowArtifactService;
import com.polaris.ai.workflow.spi.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/** 确定性内置节点配置；依赖连接器的节点单独注册。 */
@Configuration
public class BuiltInWorkflowNodeConfig {

    @Bean
    public WorkflowNodeHandler transformWorkflowNodeHandler() {
        return passThrough("transform", "数据转换", "data", emptyConfigSchema());
    }

    @Bean
    public WorkflowNodeHandler conditionWorkflowNodeHandler() {
        return passThrough("condition", "条件判断", "control", emptyConfigSchema());
    }

    @Bean
    public WorkflowNodeHandler parallelWorkflowNodeHandler() {
        return passThrough("parallel", "并行分支", "control", emptyConfigSchema());
    }

    @Bean
    public WorkflowNodeHandler joinWorkflowNodeHandler() {
        return passThrough("join", "分支汇聚", "control", joinConfigSchema());
    }

    @Bean
    public WorkflowNodeHandler loopWorkflowNodeHandler() {
        return passThrough("loop", "受控循环", "control", loopConfigSchema());
    }

    @Bean
    public WorkflowNodeHandler approvalWorkflowNodeHandler() {
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "approval", "1.0", "人工审批", "control",
                approvalConfigSchema(), JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(), WorkflowSideEffect.NONE, Set.of(),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.MOCKABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return markerHandler(descriptor);
    }

    @Bean
    public WorkflowNodeHandler waitWorkflowNodeHandler() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putArray("required").add("delaySeconds");
        ObjectNode delay = schema.putObject("properties").putObject("delaySeconds");
        delay.put("type", "integer");
        delay.put("minimum", 1);
        delay.put("maximum", 604800);
        delay.put("description", "持久化等待秒数，不占用Worker线程");
        schema.put("additionalProperties", false);
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "wait", "1.0", "定时等待", "control", schema,
                JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.objectNode(),
                WorkflowSideEffect.NONE, Set.of(),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.MOCKABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return markerHandler(descriptor);
    }

    @Bean
    public WorkflowNodeHandler artifactWorkflowNodeHandler(
            WorkflowArtifactService artifactService) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("fileName").put("type", "string")
                .put("minLength", 1).put("maxLength", 200);
        properties.putObject("mimeType").put("type", "string")
                .put("pattern", "^[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+$");
        properties.putObject("retentionDays").put("type", "integer")
                .put("minimum", 1).put("maximum", 3650);
        schema.put("additionalProperties", false);
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "artifact", "1.0", "保存产物", "data", schema,
                JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.objectNode(),
                WorkflowSideEffect.NONE, Set.of(),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.RETRYABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return new WorkflowNodeHandler() {
            @Override
            public WorkflowNodeDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public WorkflowNodeResult execute(WorkflowNodeContext context) {
                context.cancellation().throwIfCancellationRequested();
                WorkflowArtifactView artifact = artifactService.storeJson(
                        context.tenantId(), context.executionId(), context.nodeRunId(),
                        context.input(), context.config().path("fileName").asText(null),
                        context.config().path("mimeType").asText("application/json"),
                        context.config().path("retentionDays").asInt(30));
                ObjectNode output = JsonNodeFactory.instance.objectNode();
                output.put("artifactId", artifact.artifactId());
                output.put("fileName", artifact.fileName());
                output.put("mimeType", artifact.mimeType());
                output.put("sizeBytes", artifact.sizeBytes());
                output.put("contentHash", artifact.contentHash());
                return WorkflowNodeResult.success(output);
            }
        };
    }

    @Bean
    public WorkflowNodeHandler subWorkflowNodeHandler() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putArray("required").add("workflowCode").add("workflowVersionId");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("workflowCode").put("type", "string")
                .put("pattern", "^[A-Za-z][A-Za-z0-9_.-]{0,63}$");
        properties.putObject("workflowVersionId").put("type", "string")
                .put("minLength", 1).put("maxLength", 64);
        properties.putObject("pollSeconds").put("type", "integer")
                .put("minimum", 1).put("maximum", 60);
        schema.put("additionalProperties", false);
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "sub_workflow", "1.0", "子工作流", "control", schema,
                JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.objectNode(),
                WorkflowSideEffect.NONE, Set.of(),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return markerHandler(descriptor);
    }

    private WorkflowNodeHandler passThrough(
            String type, String name, String category, ObjectNode configSchema) {
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                type,
                "1.0",
                name,
                category,
                configSchema,
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(),
                WorkflowSideEffect.NONE,
                Set.of(),
                Set.of(
                        WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.RETRYABLE,
                        WorkflowNodeCapability.MOCKABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return new WorkflowNodeHandler() {
            @Override
            public WorkflowNodeDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public WorkflowNodeResult execute(WorkflowNodeContext context) {
                context.cancellation().throwIfCancellationRequested();
                return WorkflowNodeResult.success(context.input());
            }
        };
    }

    private ObjectNode emptyConfigSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode joinConfigSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putArray("required").add("mode");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("mode").put("type", "string")
                .putArray("enum").add("ANY").add("ALL").add("N_OF_M");
        properties.putObject("requiredBranches").put("type", "integer")
                .put("minimum", 1).put("maximum", 100);
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode loopConfigSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putArray("required").add("maxIterations");
        schema.putObject("properties").putObject("maxIterations")
                .put("type", "integer").put("minimum", 1).put("maximum", 1000);
        schema.put("additionalProperties", false);
        return schema;
    }

    private WorkflowNodeHandler markerHandler(WorkflowNodeDescriptor descriptor) {
        return new WorkflowNodeHandler() {
            @Override
            public WorkflowNodeDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public WorkflowNodeResult execute(WorkflowNodeContext context) {
                throw new IllegalStateException("持久化控制节点必须由工作流执行引擎调度");
            }
        };
    }

    private ObjectNode approvalConfigSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putArray("required")
                .add("assigneeType").add("assigneeIds").add("approvalMode")
                .add("timeoutSeconds");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("assigneeType").put("type", "string")
                .putArray("enum").add("USER").add("ROLE").add("DEPARTMENT");
        ObjectNode ids = properties.putObject("assigneeIds");
        ids.put("type", "array");
        ids.put("minItems", 1);
        ids.put("uniqueItems", true);
        ids.putObject("items").put("type", "string").put("minLength", 1).put("maxLength", 128);
        properties.putObject("approvalMode").put("type", "string")
                .putArray("enum").add("ANY").add("ALL").add("SEQUENTIAL").add("N_OF_M");
        properties.putObject("requiredApprovals").put("type", "integer")
                .put("minimum", 1).put("maximum", 100);
        properties.putObject("allowSelfApproval").put("type", "boolean");
        properties.putObject("timeoutSeconds").put("type", "integer")
                .put("minimum", 60).put("maximum", 604800);
        schema.put("additionalProperties", false);
        return schema;
    }
}
