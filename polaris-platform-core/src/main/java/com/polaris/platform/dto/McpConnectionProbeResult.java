package com.polaris.platform.dto;

import java.util.List;
import java.util.Map;

/** 完整工具发现结果，不代表审批通过，也不提供执行入口。 */
public record McpConnectionProbeResult(String protocolVersion, String serverName,
        String serverVersion, boolean toolsSupported, long durationMs, List<ToolPreview> tools) {

    public record ToolPreview(String name, String title, String description,
            Map<String, Object> inputSchema, Map<String, Object> outputSchema,
            Boolean readOnlyHint, Boolean destructiveHint, Map<String, Object> definition) {
    }
}
