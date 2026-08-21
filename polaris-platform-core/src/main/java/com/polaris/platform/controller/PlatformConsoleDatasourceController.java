package com.polaris.platform.controller;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.AjaxResult;
import com.polaris.common.core.page.TableDataInfo;
import com.polaris.platform.domain.PlatformDatasource;
import com.polaris.platform.service.PlatformDatasourceService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 外部数据源控制器
 */
@RestController
@RequestMapping("/platform/console/datasource")
public class PlatformConsoleDatasourceController extends BaseController {

    @Autowired
    private PlatformDatasourceService datasourceService;

    @Data
    public static class QueryRequest {
        private String sql;
        private int maxRows;
    }

    @GetMapping("/list")
    public TableDataInfo list(PlatformDatasource query) {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx != null && ctx.getTenantId() != null) {
            query.setTenantId(Long.parseLong(ctx.getTenantId()));
        }
        startPage();
        List<PlatformDatasource> list = datasourceService.list(query);
        return getDataTable(list);
    }

    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(datasourceService.getById(id));
    }

    @PostMapping
    public AjaxResult add(@RequestBody PlatformDatasource ds) {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx != null && ctx.getTenantId() != null) {
            ds.setTenantId(Long.parseLong(ctx.getTenantId()));
            ds.setCreateBy(ctx.getUsername());
        }
        return toAjax(datasourceService.insert(ds));
    }

    @PutMapping
    public AjaxResult edit(@RequestBody PlatformDatasource ds) {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx != null) {
            ds.setUpdateBy(ctx.getUsername());
        }
        return toAjax(datasourceService.update(ds));
    }

    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        return toAjax(datasourceService.deleteById(id));
    }

    @PostMapping("/test")
    public AjaxResult testConnection(@RequestBody PlatformDatasource ds) {
        try {
            boolean connected = datasourceService.testConnection(ds);
            return success(connected ? "连接成功" : "连接失败");
        } catch (Exception e) {
            return error("连接失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/query")
    public AjaxResult executeQuery(@PathVariable Long id, @RequestBody QueryRequest req) {
        try {
            return success(datasourceService.executeQuery(id, req.getSql(), req.getMaxRows()));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }
}
