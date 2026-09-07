package com.polaris.ai.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.workflow.application.WorkflowArtifactView;
import com.polaris.ai.workflow.service.WorkflowArtifactService;
import com.polaris.ai.workflow.spi.*;
import com.polaris.common.exception.ServiceException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/** “保存产物”节点：只保存平台内部私有文件，并提供无落盘预览。 */
@Configuration
public class WorkflowArtifactNodeConfig {

    @Bean
    public WorkflowNodeHandler artifactWorkflowNodeHandler(
            WorkflowArtifactService artifactService) {
        return new ArtifactHandler(artifactService, descriptor());
    }

    private WorkflowNodeDescriptor descriptor() {
        return new WorkflowNodeDescriptor(
                "artifact", "2.0", "保存产物", "data",
                configSchema(), inputSchema(), outputSchema(),
                WorkflowSideEffect.DURABLE_INTERNAL, Set.of(),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.RETRYABLE,
                        WorkflowNodeCapability.PREVIEWABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE,
                        WorkflowNodeCapability.IDEMPOTENCY_KEY));
    }

    private ObjectNode configSchema() {
        ObjectNode schema = objectSchema();
        schema.putArray("required")
                .add("configVersion").add("format")
                .add("fileNameTemplate").add("retentionMode");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("configVersion").put("const", "2.0")
                .put("title", "配置版本");
        properties.putObject("format").put("type", "string")
                .put("title", "保存格式").putArray("enum").add("JSON").add("TEXT");
        properties.putObject("fileNameTemplate").put("type", "string")
                .put("title", "文件名").put("minLength", 1).put("maxLength", 180)
                .put("description", "无需填写扩展名；支持{{date}}、{{executionId}}、{{nodeRunId}}");
        properties.putObject("retentionMode").put("type", "string")
                .put("title", "保留方式").putArray("enum").add("EXECUTION").add("DAYS");
        properties.putObject("retentionDays").put("type", "integer")
                .put("title", "执行结束后保留天数").put("minimum", 7).put("maximum", 90);
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode inputSchema() {
        ObjectNode schema = objectSchema();
        schema.putArray("required").add("content");
        ObjectNode content = schema.putObject("properties").putObject("content");
        content.put("title", "要保存的内容");
        content.put("description", "选择流程输入或上游节点的一项输出");
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode outputSchema() {
        ObjectNode schema = objectSchema();
        schema.putArray("required").add("artifact");
        ObjectNode artifact = schema.putObject("properties").putObject("artifact");
        artifact.put("type", "object");
        ObjectNode fields = artifact.putObject("properties");
        fields.putObject("artifactId").put("type", "string");
        fields.putObject("fileName").put("type", "string");
        fields.putObject("mediaType").put("type", "string");
        fields.putObject("sizeBytes").put("type", "integer");
        fields.putObject("status").put("type", "string");
        fields.putObject("createdAt").put("type", "string");
        fields.putObject("expiresAt").put("type", "string");
        fields.putObject("sha256").put("type", "string");
        artifact.putArray("required")
                .add("fileName").add("mediaType").add("sizeBytes")
                .add("status").add("sha256");
        artifact.put("additionalProperties", false);
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode objectSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        return schema;
    }

    private static final class ArtifactHandler
            implements WorkflowNodeHandler, WorkflowNodePreviewer {

        private final WorkflowArtifactService artifactService;
        private final WorkflowNodeDescriptor descriptor;

        private ArtifactHandler(
                WorkflowArtifactService artifactService,
                WorkflowNodeDescriptor descriptor) {
            this.artifactService = artifactService;
            this.descriptor = descriptor;
        }

        @Override
        public WorkflowNodeDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public WorkflowNodeResult execute(WorkflowNodeContext context) {
            context.cancellation().throwIfCancellationRequested();
            JsonNode content = requireContent(context);
            WorkflowArtifactView artifact = artifactService.storeNodeArtifact(
                    context.tenantId(), context.executionId(), context.nodeRunId(), content,
                    context.config().path("format").asText("JSON"),
                    context.config().path("fileNameTemplate").asText("工作流产物-{{date}}"),
                    retentionDays(context.config()));
            ObjectNode metadata = JsonNodeFactory.instance.objectNode();
            metadata.put("artifactId", artifact.artifactId());
            metadata.put("fileName", artifact.fileName());
            metadata.put("mediaType", artifact.mediaType());
            metadata.put("sizeBytes", artifact.sizeBytes());
            metadata.put("status", artifact.status());
            metadata.put("sha256", artifact.sha256());
            if (artifact.createTime() != null) {
                metadata.put("createdAt", artifact.createTime().toInstant().toString());
            }
            if (artifact.expiresTime() != null) {
                metadata.put("expiresAt", artifact.expiresTime().toInstant().toString());
            }
            ObjectNode output = JsonNodeFactory.instance.objectNode();
            output.set("artifact", metadata);
            return WorkflowNodeResult.success(output, "COMMITTED");
        }

        @Override
        public WorkflowNodeResult preview(WorkflowNodeContext context) {
            context.cancellation().throwIfCancellationRequested();
            JsonNode content = requireContent(context);
            WorkflowArtifactService.PreparedArtifact artifact =
                    artifactService.previewNodeArtifact(
                            content, context.config().path("format").asText("JSON"),
                            context.config().path("fileNameTemplate")
                                    .asText("工作流产物-{{date}}"));
            ObjectNode metadata = JsonNodeFactory.instance.objectNode();
            metadata.put("fileName", artifact.fileName());
            metadata.put("mediaType", artifact.mediaType());
            metadata.put("sizeBytes", artifact.content().length);
            metadata.put("status", "SIMULATED");
            metadata.put("sha256", artifact.sha256());
            ObjectNode output = JsonNodeFactory.instance.objectNode();
            output.set("artifact", metadata);
            return WorkflowNodeResult.success(output);
        }

        private JsonNode requireContent(WorkflowNodeContext context) {
            JsonNode input = context.input();
            if (input == null || !input.isObject() || !input.has("content")) {
                throw new ServiceException("请选择要保存的内容");
            }
            return input.get("content");
        }

        private int retentionDays(JsonNode config) {
            return "DAYS".equals(config.path("retentionMode").asText("EXECUTION"))
                    ? config.path("retentionDays").asInt(30) : 30;
        }
    }
}
