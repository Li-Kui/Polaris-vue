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
import com.polaris.platform.domain.PlatformDatasource;
import com.polaris.platform.dto.DatasourceQueryRequest;
import com.polaris.platform.service.IPlatformDatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 中台外部数据源管理控制器
 *
 * @author polaris
 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "中台外部数据源管理")
@RestController
@RequestMapping("/platform/console/datasource")
public class PlatformConsoleDatasourceController extends BaseController {

    @Autowired
    private IPlatformDatasourceService platformDatasourceService;

    /**
     * 查询当前租户的外部数据源列表
     */
    @Operation(summary = "查询当前租户的外部数据源列表")
    @GetMapping("/list")
    public ResultData<Page<PlatformDatasource>> list(PlatformDatasource datasource) {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext != null && callerContext.getTenantId() != null) {
            datasource.setTenantId(Long.parseLong(callerContext.getTenantId()));
        }
        startPage();
        List<PlatformDatasource> list = platformDatasourceService.selectDatasourceList(datasource);
        return ResultData.ok(Page.of(PageInfo.of(list)));
    }

    /**
     * 获取外部数据源详细信息
     */
    @Operation(summary = "获取外部数据源详细信息")
    @GetMapping("/{id}")
    public ResultData<PlatformDatasource> getInfo(@PathVariable Long id) {
        return ResultData.ok(platformDatasourceService.selectDatasourceById(id));
    }

    /**
     * 新增外部数据源
     */
    @Operation(summary = "新增外部数据源")
    @Log(title = "中台外部数据源管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData add(@RequestBody PlatformDatasource datasource) {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext != null && callerContext.getTenantId() != null) {
            datasource.setTenantId(Long.parseLong(callerContext.getTenantId()));
            datasource.setCreateBy(callerContext.getUsername());
        }
        return toAjaxResult(platformDatasourceService.insertDatasource(datasource));
    }

    /**
     * 修改外部数据源
     */
    @Operation(summary = "修改外部数据源")
    @Log(title = "中台外部数据源管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public ResultData edit(@RequestBody PlatformDatasource datasource) {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext != null) {
            datasource.setUpdateBy(callerContext.getUsername());
        }
        return toAjaxResult(platformDatasourceService.updateDatasource(datasource));
    }

    /**
     * 删除外部数据源
     */
    @Operation(summary = "删除外部数据源")
    @Log(title = "中台外部数据源管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public ResultData remove(@PathVariable Long id) {
        return toAjaxResult(platformDatasourceService.deleteDatasourceById(id));
    }

    /**
     * 测试外部数据源连接
     */
    @Operation(summary = "测试外部数据源连接")
    @PostMapping("/test")
    public ResultData testConnection(@RequestBody PlatformDatasource datasource) {
        try {
            boolean connected = platformDatasourceService.testDatasourceConnection(datasource);
            return ok(connected ? "连接成功" : "连接失败");
        } catch (Exception e) {
            return fail("连接失败: " + e.getMessage());
        }
    }

    /**
     * 执行外部数据源查询
     */
    @Operation(summary = "执行外部数据源查询")
    @PostMapping("/{id}/query")
    public ResultData<List<Map<String, Object>>> executeQuery(
            @PathVariable Long id, @RequestBody DatasourceQueryRequest request) {
        try {
            return ResultData.ok(platformDatasourceService.executeDatasourceQuery(
                    id, request.getSql(), request.getMaxRows()));
        } catch (Exception e) {
            return ResultData.fail(e.getMessage());
        }
    }
}
