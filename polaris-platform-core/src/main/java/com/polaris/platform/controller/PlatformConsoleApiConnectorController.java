package com.polaris.platform.controller;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.AjaxResult;
import com.polaris.common.core.page.TableDataInfo;
import com.polaris.platform.domain.PlatformApiConnector;
import com.polaris.platform.service.PlatformApiConnectorService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 第三方 API 连接器控制器
 */
@RestController
@RequestMapping("/platform/console/connector")
public class PlatformConsoleApiConnectorController extends BaseController {

    @Autowired
    private PlatformApiConnectorService connectorService;

    @Data
    public static class InvokeRequest {
        private String path;
        private String method;
        private Object body;
        private Map<String, String> queryParams;
    }

    @GetMapping("/list")
    public TableDataInfo list(PlatformApiConnector query) {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx != null && ctx.getTenantId() != null) {
            query.setTenantId(Long.parseLong(ctx.getTenantId()));
        }
        startPage();
        List<PlatformApiConnector> list = connectorService.list(query);
        return getDataTable(list);
    }

    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(connectorService.getById(id));
    }

    @PostMapping
    public AjaxResult add(@RequestBody PlatformApiConnector connector) {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx != null && ctx.getTenantId() != null) {
            connector.setTenantId(Long.parseLong(ctx.getTenantId()));
            connector.setCreateBy(ctx.getUsername());
        }
        return toAjax(connectorService.insert(connector));
    }

    @PutMapping
    public AjaxResult edit(@RequestBody PlatformApiConnector connector) {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx != null) {
            connector.setUpdateBy(ctx.getUsername());
        }
        return toAjax(connectorService.update(connector));
    }

    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        return toAjax(connectorService.deleteById(id));
    }

    @PostMapping("/{id}/invoke")
    public AjaxResult invoke(@PathVariable Long id, @RequestBody InvokeRequest req) {
        try {
            HttpMethod method = req.getMethod() != null ? HttpMethod.valueOf(req.getMethod().toUpperCase()) : HttpMethod.GET;
            ResponseEntity<String> response = connectorService.execute(id, req.getPath(), method, req.getBody(), req.getQueryParams());
            return success(response.getBody());
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }
}
