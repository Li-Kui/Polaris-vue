package com.polaris.platform.controller;

import com.github.pagehelper.PageInfo;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.core.page.Page;
import com.polaris.common.enums.BusinessType;
import com.polaris.platform.domain.PlatformApiConnector;
import com.polaris.platform.dto.ApiConnectorResponse;
import com.polaris.platform.dto.ApiConnectorSaveRequest;
import com.polaris.platform.dto.ConnectorInvokeRequest;
import com.polaris.platform.service.IPlatformApiConnectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 中台第三方 API 连接器管理控制器
 *
 * @author polaris
 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "中台API连接器管理")
@RestController
@RequestMapping("/platform/console/connector")
public class PlatformConsoleApiConnectorController extends BaseController {

    @Autowired
    private IPlatformApiConnectorService platformApiConnectorService;

    /**
     * 查询当前租户的 API 连接器列表
     */
    @Operation(summary = "查询当前租户的 API 连接器列表")
    @GetMapping("/list")
    public ResultData<Page<ApiConnectorResponse>> list(PlatformApiConnector connector) {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext != null && callerContext.getTenantId() != null) {
            connector.setTenantId(Long.parseLong(callerContext.getTenantId()));
        }
        startPage();
        List<PlatformApiConnector> list = platformApiConnectorService.selectConnectorList(connector);
        return ResultData.ok(Page.of(PageInfo.of(list), ApiConnectorResponse::from));
    }

    /**
     * 获取 API 连接器详细信息
     */
    @Operation(summary = "获取 API 连接器详细信息")
    @GetMapping("/{id}")
    public ResultData<ApiConnectorResponse> getInfo(@PathVariable Long id) {
        return ResultData.ok(ApiConnectorResponse.from(
                platformApiConnectorService.selectConnectorById(id)));
    }

    /**
     * 新增 API 连接器
     */
    @Operation(summary = "新增 API 连接器")
    @Log(title = "中台 API 连接器管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData<ApiConnectorResponse> add(@RequestBody ApiConnectorSaveRequest request) {
        CallerContext callerContext = CallerContextHolder.get();
        String operator = callerContext == null ? null : callerContext.getUsername();
        return ResultData.ok(ApiConnectorResponse.from(
                platformApiConnectorService.insertConnector(request, operator)));
    }

    /**
     * 修改 API 连接器
     */
    @Operation(summary = "修改 API 连接器")
    @Log(title = "中台 API 连接器管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public ResultData<ApiConnectorResponse> edit(@RequestBody ApiConnectorSaveRequest request) {
        CallerContext callerContext = CallerContextHolder.get();
        String operator = callerContext == null ? null : callerContext.getUsername();
        return ResultData.ok(ApiConnectorResponse.from(
                platformApiConnectorService.updateConnector(request, operator)));
    }

    /**
     * 删除 API 连接器
     */
    @Operation(summary = "删除 API 连接器")
    @Log(title = "中台 API 连接器管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public ResultData remove(@PathVariable Long id) {
        return toAjaxResult(platformApiConnectorService.deleteConnectorById(id));
    }

    /**
     * 查询连接器当前被工作流引用的数量。
     */
    @Operation(summary = "查询 API 连接器引用数量")
    @GetMapping("/{id}/usages")
    public ResultData<Map<String, Long>> usages(@PathVariable Long id) {
        return ResultData.ok(Map.of(
                "activeBindingCount", platformApiConnectorService.countConnectorUsages(id)));
    }

    /**
     * 调用指定 API 连接器
     */
    @Operation(summary = "调用指定 API 连接器")
    @PostMapping("/{id}/invoke")
    public ResultData invoke(@PathVariable Long id, @RequestBody ConnectorInvokeRequest request) {
        try {
            HttpMethod method = request.getMethod() != null
                    ? HttpMethod.valueOf(request.getMethod().toUpperCase()) : HttpMethod.GET;
            ResponseEntity<String> response = platformApiConnectorService.invokeConnector(
                    id, request.getPath(), method, request.getBody(), request.getQueryParams());
            return ok(response.getBody());
        } catch (Exception e) {
            return fail(e.getMessage());
        }
    }
}
