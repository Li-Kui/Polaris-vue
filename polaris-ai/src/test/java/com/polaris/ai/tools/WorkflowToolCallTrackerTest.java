package com.polaris.ai.tools;

import com.polaris.ai.workflow.spi.WorkflowToolCallObserver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowToolCallTrackerTest {

    @Test
    void recordsSuccessfulFailedAndBlockedCallsWithoutPayloads() {
        List<WorkflowToolCallObserver.Event> events = new ArrayList<>();
        WorkflowToolCallTracker tracker = new WorkflowToolCallTracker(events::add);

        int first = tracker.reserve("queryUsers", 2);
        tracker.succeeded("queryUsers", first, 12, true);
        int second = tracker.reserve("queryLogs", 2);
        tracker.failed("queryLogs", second, 8, "TOOL_EXECUTION_FAILED");
        assertEquals(-1, tracker.reserve("queryUsers", 2));

        Map<String, Number> usage = tracker.usage();
        assertEquals(3, usage.get("toolAttempts"));
        assertEquals(2, usage.get("toolCalls"));
        assertEquals(1, usage.get("toolSucceeded"));
        assertEquals(1, usage.get("toolFailed"));
        assertEquals(1, usage.get("toolBlocked"));
        assertEquals(20L, usage.get("toolDurationMs"));
        assertEquals(1, usage.get("toolResultTruncated"));
        assertEquals(List.of(
                WorkflowToolCallObserver.Status.STARTED,
                WorkflowToolCallObserver.Status.SUCCEEDED,
                WorkflowToolCallObserver.Status.STARTED,
                WorkflowToolCallObserver.Status.FAILED,
                WorkflowToolCallObserver.Status.BLOCKED),
                events.stream().map(WorkflowToolCallObserver.Event::status).toList());
        assertEquals("TOOL_CALL_LIMIT", events.get(4).reasonCode());
    }

    @Test
    void recordsRevokedPermissionAsBlockedWithoutStartingTool() {
        List<WorkflowToolCallObserver.Event> events = new ArrayList<>();
        WorkflowToolCallTracker tracker = new WorkflowToolCallTracker(events::add);

        tracker.blocked("queryUsers", "PERMISSION_REVOKED");

        assertEquals(1, tracker.usage().get("toolAttempts"));
        assertEquals(0, tracker.usage().get("toolCalls"));
        assertEquals(1, tracker.usage().get("toolBlocked"));
        assertEquals("PERMISSION_REVOKED", events.get(0).reasonCode());
    }
}
