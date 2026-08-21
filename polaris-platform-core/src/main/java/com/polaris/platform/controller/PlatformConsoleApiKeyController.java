package com.polaris.platform.controller;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.AjaxResult;
import com.polaris.common.core.page.TableDataInfo;
import com.polaris.platform.domain.PlatformApiKey;
import com.polaris.platform.service.PlatformApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 中台 API Key 管理控制器
 */
@RestController
@RequestMapping("/platform/console/apikey")
public class PlatformConsoleApiKeyController extends BaseController {

    @Autowired
    private PlatformApiKeyService apiKeyService;

    @GetMapping("/list")
    public TableDataInfo list() {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            return getDataTable(List.of());
        }
        startPage();
        List<PlatformApiKey> list = apiKeyService.listByTenantId(Long.parseLong(ctx.getTenantId()));
        return getDataTable(list);
    }

    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(apiKeyService.getById(id));
    }

    @PostMapping
    public AjaxResult add(@RequestBody PlatformApiKey apiKey) {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            return AjaxResult.error(401, "请先登录");
        }
        apiKey.setTenantId(Long.parseLong(ctx.getTenantId()));
        apiKey.setCreateBy(ctx.getUsername());
        return success(apiKeyService.create(apiKey));
    }

    @PutMapping
    public AjaxResult edit(@RequestBody PlatformApiKey apiKey) {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx != null) {
            apiKey.setUpdateBy(ctx.getUsername());
        }
        return toAjax(apiKeyService.update(apiKey));
    }

    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        return toAjax(apiKeyService.delete(id));
    }
}
