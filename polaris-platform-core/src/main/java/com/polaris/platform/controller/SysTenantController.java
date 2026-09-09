package com.polaris.platform.controller;

import com.github.pagehelper.PageInfo;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.core.page.Page;
import com.polaris.common.enums.BusinessType;
import com.polaris.platform.domain.Tenant;
import com.polaris.platform.dto.CreateTenantRequest;
import com.polaris.platform.service.ITenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理后台 - 中台租户管理控制器
 *
 * @author polaris
 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "中台租户管理")
@RestController
@RequestMapping("/system/platform/tenant")
public class SysTenantController extends BaseController {

    @Autowired
    private ITenantService tenantService;

    /**
     * 查询租户列表
     */
    @Operation(summary = "查询租户列表")
    @PreAuthorize("@ss.hasPermi('system:tenant:list')")
    @GetMapping("/list")
    public ResultData<Page<Tenant>> list(Tenant tenant) {
        startPage();
        List<Tenant> list = tenantService.selectTenantList(tenant);
        return ResultData.ok(Page.of(PageInfo.of(list)));
    }

    /**
     * 获取租户详细信息
     */
    @Operation(summary = "获取租户详细信息")
    @PreAuthorize("@ss.hasPermi('system:tenant:query')")
    @GetMapping("/{tenantId}")
    public ResultData<Tenant> getInfo(@PathVariable Long tenantId) {
        return ResultData.ok(tenantService.selectTenantById(tenantId));
    }

    /**
     * 新增租户及初始管理员
     */
    @Operation(summary = "新增租户及初始管理员")
    @PreAuthorize("@ss.hasPermi('system:tenant:add')")
    @Log(title = "租户管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData<Tenant> add(@Validated @RequestBody CreateTenantRequest request) {
        request.getTenant().setCreateBy(getUsername());
        Tenant createdTenant = tenantService.createTenant(
                request.getTenant(), request.getAdminUsername(), request.getAdminPassword());
        return ResultData.ok(createdTenant);
    }

    /**
     * 修改租户
     */
    @Operation(summary = "修改租户")
    @PreAuthorize("@ss.hasPermi('system:tenant:edit')")
    @Log(title = "租户管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public ResultData edit(@RequestBody Tenant tenant) {
        tenant.setUpdateBy(getUsername());
        return toAjaxResult(tenantService.updateTenant(tenant));
    }

    /**
     * 删除租户
     */
    @Operation(summary = "删除租户")
    @PreAuthorize("@ss.hasPermi('system:tenant:remove')")
    @Log(title = "租户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{tenantId}")
    public ResultData remove(@PathVariable Long tenantId) {
        return toAjaxResult(tenantService.deleteTenant(tenantId));
    }
}
