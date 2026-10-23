package com.polaris.platform.dto;

import com.polaris.platform.domain.PlatformMcpConnection;
import com.polaris.platform.domain.PlatformMcpTool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Date;
import java.util.Objects;

/** policyReady仅表示本地审批就绪，调用前仍复查远端定义与当前授权。 */
public record McpToolResponse(Long id, Long connectionId, String toolName, String toolTitle,
        String description, JsonNode inputSchema, JsonNode outputSchema, JsonNode definition,
        String structureHash, Long revision, String catalogStatus, String reviewStatus,
        String enabled, String classification, boolean schemaSupported, String schemaError,
        String reviewedBy, Date reviewedTime, String reviewNote, Date lastSeenTime,
        Long connectionRevision, Long catalogRevision, boolean approvable, boolean policyReady, String unavailableReason) {
    public static McpToolResponse from(PlatformMcpTool tool, PlatformMcpConnection connection) {
        String reason = null;
        if (!"0".equals(connection.getStatus())) reason = "连接已停用";
        else if (tool.getConnectionEpoch() == null || !Objects.equals(tool.getConnectionEpoch(), connection.getCatalogEpoch())
                || "STALE".equals(tool.getCatalogStatus())) reason = "连接认证上下文已变化，请重新刷新和审批";
        else if (!"PRESENT".equals(tool.getCatalogStatus())) reason = "远端工具已不存在";
        else if (!"0".equals(tool.getSchemaSupported())) reason = tool.getSchemaError() == null ? "Schema不受支持" : tool.getSchemaError();
        else if (!"APPROVED".equals(tool.getReviewStatus()) || !"0".equals(tool.getEnabled())
                || !"READ_ONLY".equals(tool.getClassification())
                || tool.getRevision() == null || tool.getReviewedRevision() == null
                || !Objects.equals(tool.getRevision(), tool.getReviewedRevision())) reason = "工具尚未通过当前版本的只读审批";
        JsonMapper json = JsonMapper.shared();
        boolean approvable = "0".equals(connection.getStatus()) && "PRESENT".equals(tool.getCatalogStatus())
                && "0".equals(tool.getSchemaSupported())
                && tool.getConnectionEpoch() != null && Objects.equals(tool.getConnectionEpoch(), connection.getCatalogEpoch());
        return new McpToolResponse(tool.getId(), tool.getConnectionId(), tool.getToolName(), tool.getToolTitle(),
                tool.getDescription(), json.readTree(tool.getInputSchema()),
                tool.getOutputSchema() == null ? null : json.readTree(tool.getOutputSchema()),
                json.readTree(tool.getDefinitionJson()), tool.getStructureHash(), tool.getRevision(),
                tool.getCatalogStatus(), tool.getReviewStatus(), tool.getEnabled(), tool.getClassification(),
                "0".equals(tool.getSchemaSupported()), tool.getSchemaError(), tool.getReviewedBy(), tool.getReviewedTime(),
                tool.getReviewNote(), tool.getLastSeenTime(), connection.getRevision(), connection.getCatalogRevision(),
                approvable, reason == null, reason);
    }
}
