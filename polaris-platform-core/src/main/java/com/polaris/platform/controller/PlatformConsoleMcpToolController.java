package com.polaris.platform.controller;

import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.core.page.Page;
import com.polaris.platform.dto.McpCatalogRefreshResult;
import com.polaris.platform.dto.McpToolPolicyRequest;
import com.polaris.platform.dto.McpToolResponse;
import com.polaris.platform.service.IPlatformMcpToolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "中台MCP工具目录")
@RestController
@RequestMapping("/platform/console/mcp")
public class PlatformConsoleMcpToolController {
    private final IPlatformMcpToolService service;

    public PlatformConsoleMcpToolController(IPlatformMcpToolService service) { this.service = service; }

    @GetMapping("/{connectionId}/tools")
    public ResultData<Page<McpToolResponse>> list(@PathVariable Long connectionId,
            Integer pageNum, Integer pageSize, String catalogStatus) {
        return ResultData.ok(service.selectByConnection(connectionId, pageNum, pageSize, catalogStatus));
    }

    @Operation(summary = "完整发现并事务刷新工具目录，默认不授权")
    @PostMapping("/{connectionId}/refresh")
    public ResultData<McpCatalogRefreshResult> refresh(@PathVariable Long connectionId) {
        return ResultData.ok(service.refresh(connectionId));
    }

    @Operation(summary = "基于当前快照审批只读工具或撤销审批，不执行工具")
    @PutMapping("/tools/{toolId}/policy")
    public ResultData<McpToolResponse> policy(@PathVariable Long toolId, @RequestBody McpToolPolicyRequest request) {
        return ResultData.ok(service.changePolicy(toolId, request));
    }
}
