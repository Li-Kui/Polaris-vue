package com.polaris.platform.controller;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.AjaxResult;
import com.polaris.common.core.page.TableDataInfo;
import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.mapper.PlatformUserMapper;
import com.polaris.platform.service.PlatformAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 中台租户成员管理
 */
@RestController
@RequestMapping("/platform/console/user")
public class PlatformConsoleTenantUserController extends BaseController {

    @Autowired
    private PlatformUserMapper userMapper;

    @Autowired
    private PlatformAuthService authService;

    @GetMapping("/list")
    public TableDataInfo list() {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            return getDataTable(List.of());
        }
        Long tenantId = Long.parseLong(ctx.getTenantId());
        startPage();
        List<PlatformUser> list = userMapper.selectByTenantId(tenantId);
        return getDataTable(list);
    }

    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(userMapper.selectById(id));
    }

    @PostMapping
    public AjaxResult add(@RequestBody PlatformUser user) {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            return AjaxResult.error(401, "请先登录");
        }
        user.setTenantId(Long.parseLong(ctx.getTenantId()));
        user.setCreateBy(ctx.getUsername());
        authService.createUser(user);
        return success();
    }

    @PutMapping
    public AjaxResult edit(@RequestBody PlatformUser user) {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx != null) {
            user.setUpdateBy(ctx.getUsername());
        }
        return toAjax(userMapper.update(user));
    }

    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        return toAjax(userMapper.deleteById(id));
    }
}
