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
import com.polaris.platform.domain.PlatformApiKey;
import com.polaris.platform.dto.PlatformApiKeyCreatedResponse;
import com.polaris.platform.service.IPlatformApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 中台 API Key 管理控制器
 *
 * @author polaris
 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "中台API Key管理")
@RestController
@RequestMapping("/platform/console/apikey")
public class PlatformConsoleApiKeyController extends BaseController {

    @Autowired
    private IPlatformApiKeyService platformApiKeyService;

    /**
     * 查询当前租户的 API Key 列表
     */
    @Operation(summary = "查询当前租户的 API Key 列表")
    @GetMapping("/list")
    public ResultData<Page<PlatformApiKey>> list() {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext == null || callerContext.getTenantId() == null) {
            return ResultData.ok(Page.of(PageInfo.of(List.<PlatformApiKey>of())));
        }
        startPage();
        List<PlatformApiKey> list = platformApiKeyService.selectApiKeyListByTenantId(
                Long.parseLong(callerContext.getTenantId()));
        return ResultData.ok(Page.of(PageInfo.of(list)));
    }

    /**
     * 获取 API Key 详细信息
     */
    @Operation(summary = "获取 API Key 详细信息")
    @GetMapping("/{id}")
    public ResultData<PlatformApiKey> getInfo(@PathVariable Long id) {
        return ResultData.ok(platformApiKeyService.selectApiKeyById(id));
    }

    /**
     * 新增 API Key
     */
    @Operation(summary = "新增 API Key")
    @Log(title = "中台 API Key 管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData<PlatformApiKeyCreatedResponse> add(@RequestBody PlatformApiKey apiKey) {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext == null || callerContext.getTenantId() == null) {
            return ResultData.fail(401, "请先登录");
        }
        apiKey.setTenantId(Long.parseLong(callerContext.getTenantId()));
        apiKey.setCreateBy(callerContext.getUsername());
        return ResultData.ok(platformApiKeyService.createApiKey(apiKey));
    }

    /**
     * 修改 API Key
     */
    @Operation(summary = "修改 API Key")
    @Log(title = "中台 API Key 管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public ResultData edit(@RequestBody PlatformApiKey apiKey) {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext != null) {
            apiKey.setUpdateBy(callerContext.getUsername());
        }
        return toAjaxResult(platformApiKeyService.updateApiKey(apiKey));
    }

    /**
     * 删除 API Key
     */
    @Operation(summary = "删除 API Key")
    @Log(title = "中台 API Key 管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public ResultData remove(@PathVariable Long id) {
        return toAjaxResult(platformApiKeyService.deleteApiKey(id));
    }
}
