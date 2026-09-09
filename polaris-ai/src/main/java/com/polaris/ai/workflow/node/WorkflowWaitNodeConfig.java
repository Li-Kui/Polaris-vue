package com.polaris.ai.workflow.node;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.workflow.spi.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Set;

/** “等待”节点：生产运行由执行引擎持久化调度，单节点试运行只做虚拟时间预览。 */
@Configuration
public class WorkflowWaitNodeConfig {

    @Bean
    public WorkflowNodeHandler waitWorkflowNodeHandler() {
        return new WaitHandler(descriptor());
    }

    private WorkflowNodeDescriptor descriptor() {
        return new WorkflowNodeDescriptor(
                "wait", "2.0", "等待", "control",
                configSchema(), inputSchema(), outputSchema(),
                WorkflowSideEffect.DURABLE_INTERNAL, Set.of(),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.PREVIEWABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE,
                        WorkflowNodeCapability.IDEMPOTENCY_KEY));
    }

    private ObjectNode configSchema() {
        ObjectNode schema = objectSchema();
        schema.putArray("required").add("configVersion").add("schedule")
                .add("pastDuePolicy").add("safety");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("configVersion").put("const", "2.0");

        ObjectNode schedule = properties.putObject("schedule");
        schedule.put("type", "object").put("additionalProperties", false);
        schedule.putArray("required").add("kind").add("source");
        ObjectNode scheduleProperties = schedule.putObject("properties");
        scheduleProperties.putObject("kind").put("type", "string")
                .putArray("enum").add("AFTER").add("AT");
        ObjectNode source = scheduleProperties.putObject("source");
        source.put("type", "object").put("additionalProperties", false);
        source.putArray("required").add("kind").add("unit").add("timezone");
        ObjectNode sourceProperties = source.putObject("properties");
        sourceProperties.putObject("kind").put("type", "string")
                .putArray("enum").add("FIXED").add("INPUT");
        sourceProperties.putObject("value").put("type", "integer")
                .put("minimum", 0).put("maximum", WorkflowWaitPolicy.MAX_WAIT_SECONDS);
        sourceProperties.putObject("unit").put("type", "string")
                .putArray("enum").add("SECOND").add("MINUTE").add("HOUR").add("DAY");
        sourceProperties.putObject("localDateTime").put("type", "string")
                .put("maxLength", 32);
        sourceProperties.putObject("timezone").put("type", "string")
                .put("minLength", 1).put("maxLength", 64);

        properties.putObject("pastDuePolicy").put("type", "string")
                .putArray("enum").add("CONTINUE").add("FAIL");
        ObjectNode safety = properties.putObject("safety");
        safety.put("type", "object").put("additionalProperties", false);
        safety.putArray("required").add("maxWaitSeconds");
        safety.putObject("properties").putObject("maxWaitSeconds")
                .put("type", "integer").put("minimum", 1)
                .put("maximum", WorkflowWaitPolicy.MAX_WAIT_SECONDS);
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode inputSchema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("duration").put("type", "integer")
                .put("title", "等待时长").put("minimum", 0);
        properties.putObject("targetAt").put("type", "string")
                .put("title", "目标时间").put("maxLength", 128);
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode outputSchema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("status").put("type", "string")
                .putArray("enum").add("RESUMED");
        for (String field : Set.of("enteredAt", "targetAt", "resumedAt")) {
            properties.putObject(field).put("type", "string").put("format", "date-time");
        }
        properties.putObject("waitedMs").put("type", "integer").put("minimum", 0);
        properties.putObject("lateByMs").put("type", "integer").put("minimum", 0);
        for (String field : Set.of("pastDue", "skipped", "manualResume", "simulated")) {
            properties.putObject(field).put("type", "boolean");
        }
        properties.putObject("timezone").put("type", "string").put("minLength", 1);
        schema.putArray("required")
                .add("status").add("enteredAt").add("targetAt").add("resumedAt")
                .add("waitedMs").add("lateByMs").add("pastDue").add("skipped")
                .add("manualResume").add("simulated").add("timezone");
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode objectSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        return schema;
    }

    private static final class WaitHandler
            implements WorkflowNodeHandler, WorkflowNodePreviewer {
        private final WorkflowNodeDescriptor descriptor;

        private WaitHandler(WorkflowNodeDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public WorkflowNodeDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public WorkflowNodeResult execute(WorkflowNodeContext context) {
            throw new IllegalStateException("等待节点必须由工作流执行引擎持久化调度");
        }

        @Override
        public WorkflowNodeResult preview(WorkflowNodeContext context) {
            context.cancellation().throwIfCancellationRequested();
            Instant enteredAt = Instant.now();
            WorkflowWaitPolicy.Resolution resolution = WorkflowWaitPolicy.resolve(
                    context.config(), context.input(), enteredAt);
            Instant resumedAt = resolution.skipped() ? enteredAt : resolution.targetAt();
            return WorkflowNodeResult.success(
                    WorkflowWaitPolicy.output(resolution, resumedAt, true, false));
        }
    }
}
