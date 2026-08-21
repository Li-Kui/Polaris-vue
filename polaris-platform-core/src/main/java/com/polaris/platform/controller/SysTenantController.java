package com.polaris.platform.controller;

import com.polaris.common.annotation.Log;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.AjaxResult;
import com.polaris.common.core.page.TableDataInfo;
import com.polaris.common.enums.BusinessType;
import com.polaris.platform.domain.Tenant;
import com.polaris.platform.service.TenantService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理后台 - 中台租户管理控制器
 */
@RestController
@RequestMapping("/system/platform/tenant")
public class SysTenantController extends BaseController {

    @Autowired
    private TenantService tenantService;

    @Data
    public static class CreateTenantBody {
        private Tenant tenant;
        private String adminUsername;
        private String adminPassword;
    }

    @PreAuthorize("@ss.hasPermi('system:tenant:list')")
    @GetMapping("/list")
    public TableDataInfo list(Tenant query) {
        startPage();
        List<Tenant> list = tenantService.list(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('system:tenant:query')")
    @GetMapping("/{tenantId}")
    public AjaxResult getInfo(@PathVariable Long tenantId) {
        return success(tenantService.getById(tenantId));
    }

    @PreAuthorize("@ss.hasPermi('system:tenant:add')")
    @Log(title = "租户管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CreateTenantBody body) {
        if (body.getTenant() == null || body.getAdminUsername() == null || body.getAdminPassword() == null) {
            return error("租户信息、初始管理员账号和密码不能为空");
        }
        body.getTenant().setCreateBy(getUsername());
        Tenant created = tenantService.create(body.getTenant(), body.getAdminUsername(), body.getAdminPassword());
        return success(created);
    }

    @PreAuthorize("@ss.hasPermi('system:tenant:edit')")
    @Log(title = "租户管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Tenant tenant) {
        tenant.setUpdateBy(getUsername());
        return toAjax(tenantService.update(tenant));
    }

    @PreAuthorize("@ss.hasPermi('system:tenant:remove')")
    @Log(title = "租户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{tenantId}")
    public AjaxResult remove(@PathVariable Long tenantId) {
        return toAjax(tenantService.delete(tenantId));
    }
}
