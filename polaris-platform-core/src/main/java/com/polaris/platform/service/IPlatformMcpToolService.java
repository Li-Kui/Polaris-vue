package com.polaris.platform.service;

import com.polaris.common.core.page.Page;
import com.polaris.platform.dto.McpCatalogRefreshResult;
import com.polaris.platform.dto.McpToolPolicyRequest;
import com.polaris.platform.dto.McpToolResponse;

public interface IPlatformMcpToolService {
    Page<McpToolResponse> selectByConnection(Long connectionId, Integer pageNum, Integer pageSize, String catalogStatus);
    McpCatalogRefreshResult refresh(Long connectionId);
    McpToolResponse changePolicy(Long toolId, McpToolPolicyRequest request);
}
