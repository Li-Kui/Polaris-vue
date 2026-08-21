package com.polaris.platform.controller;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.AjaxResult;
import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.domain.Tenant;
import com.polaris.platform.mapper.PlatformUserMapper;
import com.polaris.platform.mapper.TenantMapper;
import com.polaris.platform.service.PlatformAuthService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 中台控制台认证控制器
 */
@RestController
@RequestMapping("/platform")
public class PlatformConsoleAuthController extends BaseController {

    @Autowired
    private PlatformAuthService authService;

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private PlatformUserMapper userMapper;

    @Data
    public static class PlatformLoginBody {
        private String tenantCode;
        private String username;
        private String password;
    }

    /**
     * 中台用户登录
     */
    @PostMapping("/login")
    public AjaxResult login(@RequestBody PlatformLoginBody body) {
        if (body.getTenantCode() == null || body.getUsername() == null || body.getPassword() == null) {
            return error("租户编码、用户名或密码不能为空");
        }
        Tenant tenant = tenantMapper.selectByCode(body.getTenantCode());
        if (tenant == null) {
            return error("租户不存在");
        }
        if ("1".equals(tenant.getStatus())) {
            return error("租户已被停用");
        }
        try {
            Map<String, Object> authData = authService.login(tenant.getTenantId(), body.getUsername(), body.getPassword());
            return success(authData);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    /**
     * 获取当前登录租户用户信息
     */
    @GetMapping("/getInfo")
    public AjaxResult getInfo() {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            return AjaxResult.error(401, "未登录或登录已失效");
        }
        Long tenantId = Long.parseLong(ctx.getTenantId());
        Long userId = ctx.getUserId();

        Tenant tenant = tenantMapper.selectById(tenantId);
        PlatformUser user = userMapper.selectById(userId);

        Map<String, Object> res = new HashMap<>();
        res.put("user", user);
        res.put("tenant", tenant);
        res.put("permissions", ctx.isSuperAdmin() ? new String[]{"*"} : new String[]{"platform:console"});
        return success(res);
    }

    /**
     * 中台退出登录
     */
    @PostMapping("/logout")
    public AjaxResult logout() {
        CallerContextHolder.clear();
        return success("退出成功");
    }
}
