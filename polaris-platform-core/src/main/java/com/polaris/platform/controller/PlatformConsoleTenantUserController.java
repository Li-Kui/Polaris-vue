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
import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.dto.PlatformUserResponse;
import com.polaris.platform.service.IPlatformUserService;
import com.polaris.platform.tenant.PlatformTenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 中台租户成员管理控制器
 *
 * @author polaris
 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "中台租户成员管理")
@RestController
@RequestMapping("/platform/console/user")
public class PlatformConsoleTenantUserController extends BaseController {

    @Autowired
    private IPlatformUserService platformUserService;

    /**
     * 查询当前租户的成员列表
     */
    @Operation(summary = "查询当前租户的成员列表")
    @GetMapping("/list")
    public ResultData<Page<PlatformUserResponse>> list() {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext == null || callerContext.getTenantId() == null) {
            return ResultData.ok(Page.of(PageInfo.of(List.<PlatformUser>of()), PlatformUserResponse::from));
        }
        Long tenantId = Long.parseLong(callerContext.getTenantId());
        startPage();
        List<PlatformUser> list = platformUserService.selectUserListByTenantId(tenantId);
        return ResultData.ok(Page.of(PageInfo.of(list), PlatformUserResponse::from));
    }

    /**
     * 获取租户成员详细信息
     */
    @Operation(summary = "获取租户成员详细信息")
    @GetMapping("/{id}")
    public ResultData<PlatformUserResponse> getInfo(@PathVariable Long id) {
        PlatformUser user = platformUserService.selectUserById(id);
        return ResultData.ok(user != null && PlatformTenantGuard.belongsToCurrentTenant(user.getTenantId())
                ? PlatformUserResponse.from(user) : null);
    }

    /**
     * 新增租户成员
     */
    @Operation(summary = "新增租户成员")
    @Log(title = "中台租户成员管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData add(@RequestBody PlatformUser user) {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext == null || callerContext.getTenantId() == null) {
            return ResultData.fail(401, "请先登录");
        }
        user.setTenantId(Long.parseLong(callerContext.getTenantId()));
        user.setCreateBy(callerContext.getUsername());
        platformUserService.insertUser(user);
        return ok();
    }

    /**
     * 修改租户成员
     */
    @Operation(summary = "修改租户成员")
    @Log(title = "中台租户成员管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public ResultData edit(@RequestBody PlatformUser user) {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext != null) {
            user.setUpdateBy(callerContext.getUsername());
        }
        PlatformUser current = platformUserService.selectUserById(user.getId());
        if (current == null || !PlatformTenantGuard.belongsToCurrentTenant(current.getTenantId())) {
            return toAjaxResult(0);
        }
        user.setTenantId(current.getTenantId());
        return toAjaxResult(platformUserService.updateUser(user));
    }

    /**
     * 删除租户成员
     */
    @Operation(summary = "删除租户成员")
    @Log(title = "中台租户成员管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public ResultData remove(@PathVariable Long id) {
        PlatformUser user = platformUserService.selectUserById(id);
        if (user == null || !PlatformTenantGuard.belongsToCurrentTenant(user.getTenantId())) {
            return toAjaxResult(0);
        }
        return toAjaxResult(platformUserService.deleteUserById(id));
    }
}
