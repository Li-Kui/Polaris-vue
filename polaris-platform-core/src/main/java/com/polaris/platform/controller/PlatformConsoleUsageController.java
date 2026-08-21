package com.polaris.platform.controller;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.AjaxResult;
import com.polaris.platform.service.PlatformUsageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 中台控制台用量统计控制器
 */
@RestController
@RequestMapping("/platform/console/usage")
public class PlatformConsoleUsageController extends BaseController {

    @Autowired
    private PlatformUsageService usageService;

    @GetMapping
    public AjaxResult getUsage() {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            return AjaxResult.error(401, "请先登录");
        }
        Long tenantId = Long.parseLong(ctx.getTenantId());
        return success(usageService.getTenantUsage(tenantId));
    }
}
