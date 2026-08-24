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
import com.polaris.platform.dto.ConnectorInvokeRequest;
import com.polaris.platform.service.IPlatformApiConnectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResultData<Page<PlatformApiConnector>> list(PlatformApiConnector connector) {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext != null && callerContext.getTenantId() != null) {
            connector.setTenantId(Long.parseLong(callerContext.getTenantId()));
        }
        startPage();
        List<PlatformApiConnector> list = platformApiConnectorService.selectConnectorList(connector);
        return ResultData.ok(Page.of(PageInfo.of(list)));
    }

    /**
     * 获取 API 连接器详细信息
     */
    @Operation(summary = "获取 API 连接器详细信息")
    @GetMapping("/{id}")
    public ResultData<PlatformApiConnector> getInfo(@PathVariable Long id) {
        return ResultData.ok(platformApiConnectorService.selectConnectorById(id));
    }

    /**
     * 新增 API 连接器
     */
    @Operation(summary = "新增 API 连接器")
    @Log(title = "中台 API 连接器管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData add(@RequestBody PlatformApiConnector connector) {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext != null && callerContext.getTenantId() != null) {
            connector.setTenantId(Long.parseLong(callerContext.getTenantId()));
            connector.setCreateBy(callerContext.getUsername());
        }
        return toAjaxResult(platformApiConnectorService.insertConnector(connector));
    }

    /**
     * 修改 API 连接器
     */
    @Operation(summary = "修改 API 连接器")
    @Log(title = "中台 API 连接器管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public ResultData edit(@RequestBody PlatformApiConnector connector) {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext != null) {
            connector.setUpdateBy(callerContext.getUsername());
        }
        return toAjaxResult(platformApiConnectorService.updateConnector(connector));
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
