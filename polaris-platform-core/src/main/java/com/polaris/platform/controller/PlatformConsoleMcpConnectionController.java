package com.polaris.platform.controller;

import com.github.pagehelper.PageInfo;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.core.page.Page;
import com.polaris.platform.dto.McpConnectionProbeResult;
import com.polaris.platform.dto.McpConnectionResponse;
import com.polaris.platform.dto.McpConnectionSaveRequest;
import com.polaris.platform.service.IPlatformMcpConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/** 请求包含凭证，不使用会记录请求原文的通用操作日志注解。 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "中台MCP连接管理")
@RestController
@RequestMapping("/platform/console/mcp")
public class PlatformConsoleMcpConnectionController extends BaseController {
    private final IPlatformMcpConnectionService service;

    public PlatformConsoleMcpConnectionController(IPlatformMcpConnectionService service) {
        this.service = service;
    }

    @Operation(summary = "查询当前租户MCP连接")
    @GetMapping("/list")
    public ResultData<Page<McpConnectionResponse>> list(String connectionName, String status) {
        startPage();
        try {
            return ResultData.ok(Page.of(PageInfo.of(service.selectList(connectionName, status)),
                    McpConnectionResponse::from));
        } finally {
            clearPage();
        }
    }

    @GetMapping("/{id}")
    public ResultData<McpConnectionResponse> getInfo(@PathVariable Long id) {
        return ResultData.ok(McpConnectionResponse.from(service.selectById(id)));
    }

    @PostMapping
    public ResultData<McpConnectionResponse> add(@RequestBody McpConnectionSaveRequest request) {
        return ResultData.ok(McpConnectionResponse.from(service.insert(request)));
    }

    @PutMapping
    public ResultData<McpConnectionResponse> edit(@RequestBody McpConnectionSaveRequest request) {
        return ResultData.ok(McpConnectionResponse.from(service.update(request)));
    }

    @DeleteMapping("/{id}")
    public ResultData<Integer> remove(@PathVariable Long id, @RequestParam Long revision) {
        return ResultData.ok(service.delete(id, revision));
    }

    @Operation(summary = "MCP协议初始化测试，不调用工具")
    @PostMapping("/{id}/test")
    public ResultData<McpConnectionProbeResult> test(@PathVariable Long id) {
        return ResultData.ok(service.probe(id, false));
    }

    @Operation(summary = "预览远端工具，未经审批且不可执行")
    @PostMapping("/{id}/discover")
    public ResultData<McpConnectionProbeResult> discover(@PathVariable Long id) {
        return ResultData.ok(service.probe(id, true));
    }
}
