package com.polaris.platform.controller;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.AjaxResult;
import com.polaris.common.utils.ip.IpUtils;
import com.polaris.platform.auth.PlatformLoginRateLimiter;
import com.polaris.platform.auth.PlatformLoginRateLimiter.LoginAttemptResult;
import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.domain.Tenant;
import com.polaris.platform.mapper.PlatformUserMapper;
import com.polaris.platform.mapper.TenantMapper;
import com.polaris.platform.service.PlatformAuthService;
import com.polaris.platform.service.PlatformAuthService.PlatformAuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 中台控制台认证控制器
 */
@RestController
@RequestMapping("/platform")
@Slf4j
public class PlatformConsoleAuthController extends BaseController {

    @Autowired
    private PlatformAuthService authService;

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private PlatformUserMapper userMapper;

    @Autowired
    private PlatformLoginRateLimiter loginRateLimiter;

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
    public AjaxResult login(@RequestBody PlatformLoginBody body, HttpServletRequest request,
                            HttpServletResponse response) {
        if (body.getTenantCode() == null || body.getTenantCode().isBlank()
                || body.getUsername() == null || body.getUsername().isBlank()
                || body.getPassword() == null || body.getPassword().isBlank()) {
            return error("租户编码、用户名或密码不能为空");
        }

        String tenantCode = body.getTenantCode().trim();
        String username = body.getUsername().trim();
        String sourceIp = IpUtils.getIpAddr(request);
        String account = loginRateLimiter.accountFingerprint(tenantCode, username);
        LoginAttemptResult attempt;
        try {
            attempt = loginRateLimiter.tryAcquire(sourceIp, tenantCode, username);
        } catch (Exception e) {
            log.error("中台登录限流服务异常, account={}", account, e);
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            return AjaxResult.error(HttpStatus.SERVICE_UNAVAILABLE.value(), "登录服务暂不可用，请稍后重试");
        }
        if (!attempt.allowed()) {
            log.warn("中台登录尝试过于频繁, dimension={}, account={}", attempt.dimension(), account);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(attempt.retryAfterSeconds()));
            return AjaxResult.error(HttpStatus.TOO_MANY_REQUESTS.value(), "登录尝试过于频繁，请稍后重试");
        }

        try {
            Map<String, Object> authData = authService.login(tenantCode, username, body.getPassword());
            loginRateLimiter.clearAccountFailures(tenantCode, username);
            return success(authData);
        } catch (PlatformAuthenticationException e) {
            log.warn("中台登录认证失败, account={}, remoteAddress={}", account, request.getRemoteAddr());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return AjaxResult.error(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
        } catch (Exception e) {
            log.error("中台登录服务异常, account={}", account, e);
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            return AjaxResult.error(HttpStatus.SERVICE_UNAVAILABLE.value(), "登录服务暂不可用，请稍后重试");
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
