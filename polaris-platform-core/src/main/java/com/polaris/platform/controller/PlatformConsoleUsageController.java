package com.polaris.platform.controller;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.platform.dto.PlatformUsageResponse;
import com.polaris.platform.service.IPlatformUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 中台控制台用量统计控制器
 *
 * @author polaris
 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "中台用量统计")
@RestController
@RequestMapping("/platform/console/usage")
public class PlatformConsoleUsageController extends BaseController {

    @Autowired
    private IPlatformUsageService platformUsageService;

    /**
     * 获取当前租户的用量统计
     */
    @Operation(summary = "获取当前租户的用量统计")
    @GetMapping
    public ResultData<PlatformUsageResponse> getUsage() {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext == null || callerContext.getTenantId() == null) {
            return ResultData.fail(401, "请先登录");
        }
        Long tenantId = Long.parseLong(callerContext.getTenantId());
        return ResultData.ok(platformUsageService.selectTenantUsage(tenantId));
    }
}
