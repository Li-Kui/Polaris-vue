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
import com.polaris.platform.dto.DatasourceResponse;
import com.polaris.platform.dto.DatasourceSaveRequest;
import com.polaris.platform.dto.DatasourceTestResponse;
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
    public ResultData<Page<DatasourceResponse>> list(PlatformDatasource datasource) {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext != null && callerContext.getTenantId() != null) {
            datasource.setTenantId(Long.parseLong(callerContext.getTenantId()));
        }
        startPage();
        List<PlatformDatasource> list = platformDatasourceService.selectDatasourceList(datasource);
        return ResultData.ok(Page.of(PageInfo.of(list), DatasourceResponse::from));
    }

    /**
     * 获取外部数据源详细信息
     */
    @Operation(summary = "获取外部数据源详细信息")
    @GetMapping("/{id}")
    public ResultData<DatasourceResponse> getInfo(@PathVariable Long id) {
        return ResultData.ok(DatasourceResponse.from(
                platformDatasourceService.selectDatasourceById(id)));
    }

    /**
     * 新增外部数据源
     */
    @Operation(summary = "新增外部数据源")
    @Log(title = "中台外部数据源管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData<DatasourceResponse> add(@RequestBody DatasourceSaveRequest request) {
        CallerContext callerContext = CallerContextHolder.get();
        String operator = callerContext == null ? null : callerContext.getUsername();
        return ResultData.ok(DatasourceResponse.from(
                platformDatasourceService.insertDatasource(request, operator)));
    }

    /**
     * 修改外部数据源
     */
    @Operation(summary = "修改外部数据源")
    @Log(title = "中台外部数据源管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public ResultData<DatasourceResponse> edit(@RequestBody DatasourceSaveRequest request) {
        CallerContext callerContext = CallerContextHolder.get();
        String operator = callerContext == null ? null : callerContext.getUsername();
        return ResultData.ok(DatasourceResponse.from(
                platformDatasourceService.updateDatasource(request, operator)));
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

    /** 查询数据源当前被工作流引用的数量。 */
    @Operation(summary = "查询数据源引用数量")
    @GetMapping("/{id}/usages")
    public ResultData<Map<String, Long>> usages(@PathVariable Long id) {
        return ResultData.ok(Map.of(
                "activeBindingCount", platformDatasourceService.countDatasourceUsages(id)));
    }

    /**
     * 测试外部数据源连接
     */
    @Operation(summary = "测试外部数据源连接")
    @PostMapping("/test")
    public ResultData<DatasourceTestResponse> testConnection(
            @RequestBody DatasourceSaveRequest request) {
        return ResultData.ok(platformDatasourceService.testDatasourceConnection(request));
    }

    /** 测试已经保存的当前连接版本。 */
    @Operation(summary = "测试已保存的数据源连接")
    @PostMapping("/{id}/test")
    public ResultData<DatasourceTestResponse> testSavedConnection(@PathVariable Long id) {
        return ResultData.ok(platformDatasourceService.testSavedDatasourceConnection(id));
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
                    id, request.getSql(), request.getParameters(), request.getMaxRows(),
                    request.getQueryTimeoutSeconds()));
        } catch (Exception e) {
            return ResultData.fail(e.getMessage());
        }
    }
}
