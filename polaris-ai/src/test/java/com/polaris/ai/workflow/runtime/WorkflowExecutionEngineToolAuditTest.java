package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.spi.WorkflowToolCallObserver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowExecutionEngineToolAuditTest {

    @Test
    void persistsSanitizedToolLifecycleEvents() {
        RecordingPersistence persistence = new RecordingPersistence();
        WorkflowExecutionEngine engine = new WorkflowExecutionEngine(
                null, null, null, null, null, null, null,
                persistence,
                null, null, null, null, null, null, null);
        WorkflowToolCallObserver observer = engine.toolCallObserver(
                "execution-1", "runner-1", 7L, "run-1", "agent-1");

        observer.onEvent(new WorkflowToolCallObserver.Event(
                "queryTenant", 1, WorkflowToolCallObserver.Status.STARTED,
                0, null, false));
        observer.onEvent(new WorkflowToolCallObserver.Event(
                "queryTenant", 1, WorkflowToolCallObserver.Status.SUCCEEDED,
                18, null, true));

        assertEquals(List.of(
                new RecordedEvent(
                        "execution-1", "runner-1", 7L,
                        "AGENT_TOOL_STARTED", "run-1", "agent-1",
                        Map.of("toolName", "queryTenant", "callNo", 1)),
                new RecordedEvent(
                        "execution-1", "runner-1", 7L,
                        "AGENT_TOOL_SUCCEEDED", "run-1", "agent-1",
                        Map.of(
                                "toolName", "queryTenant",
                                "callNo", 1,
                                "durationMs", 18L,
                                "resultTruncated", true))),
                persistence.events);
    }

    private static class RecordingPersistence extends WorkflowExecutionPersistence {
        private final List<RecordedEvent> events = new ArrayList<>();

        RecordingPersistence() {
            super(null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public long appendEvent(
                String executionId,
                String runnerId,
                Long fencingToken,
                String eventType,
                String nodeRunId,
                String nodeId,
                Object payload) {
            events.add(new RecordedEvent(
                    executionId, runnerId, fencingToken, eventType,
                    nodeRunId, nodeId, (Map<?, ?>) payload));
            return events.size();
        }
    }

    private record RecordedEvent(
            String executionId,
            String runnerId,
            Long fencingToken,
            String eventType,
            String nodeRunId,
            String nodeId,
            Map<?, ?> payload) {
    }
}
