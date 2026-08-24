package com.polaris.platform.controller;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.utils.ip.IpUtils;
import com.polaris.platform.auth.PlatformLoginRateLimiter;
import com.polaris.platform.auth.PlatformLoginRateLimiter.LoginAttemptResult;
import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.domain.Tenant;
import com.polaris.platform.dto.PlatformLoginRequest;
import com.polaris.platform.dto.PlatformLoginResponse;
import com.polaris.platform.dto.PlatformUserInfoResponse;
import com.polaris.platform.dto.PlatformUserResponse;
import com.polaris.platform.service.IPlatformAuthService;
import com.polaris.platform.service.IPlatformUserService;
import com.polaris.platform.service.ITenantService;
import com.polaris.platform.service.PlatformAuthenticationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 中台控制台认证控制器
 *
 * @author polaris
 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "中台控制台认证")
@Slf4j
@RestController
@RequestMapping("/platform")
public class PlatformConsoleAuthController extends BaseController {

    @Autowired
    private IPlatformAuthService platformAuthService;

    @Autowired
    private ITenantService tenantService;

    @Autowired
    private IPlatformUserService platformUserService;

    @Autowired
    private PlatformLoginRateLimiter loginRateLimiter;

    /**
     * 中台用户登录
     */
    @Operation(summary = "中台用户登录")
    @PostMapping("/login")
    public ResultData<PlatformLoginResponse> login(@Validated @RequestBody PlatformLoginRequest loginRequest,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) {
        String tenantCode = loginRequest.getTenantCode().trim();
        String username = loginRequest.getUsername().trim();
        String sourceIp = IpUtils.getIpAddr(request);
        String account = loginRateLimiter.accountFingerprint(tenantCode, username);
        LoginAttemptResult attempt;
        try {
            attempt = loginRateLimiter.tryAcquire(sourceIp, tenantCode, username);
        } catch (Exception e) {
            log.error("中台登录限流服务异常, account={}", account, e);
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            return ResultData.fail(HttpStatus.SERVICE_UNAVAILABLE.value(), "登录服务暂不可用，请稍后重试");
        }
        if (!attempt.allowed()) {
            log.warn("中台登录尝试过于频繁, dimension={}, account={}", attempt.dimension(), account);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(attempt.retryAfterSeconds()));
            return ResultData.fail(HttpStatus.TOO_MANY_REQUESTS.value(), "登录尝试过于频繁，请稍后重试");
        }

        try {
            PlatformLoginResponse loginResponse = platformAuthService.login(
                    tenantCode, username, loginRequest.getPassword());
            loginRateLimiter.clearAccountFailures(tenantCode, username);
            return ResultData.ok(loginResponse);
        } catch (PlatformAuthenticationException e) {
            log.warn("中台登录认证失败, account={}, remoteAddress={}", account, request.getRemoteAddr());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return ResultData.fail(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
        } catch (Exception e) {
            log.error("中台登录服务异常, account={}", account, e);
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            return ResultData.fail(HttpStatus.SERVICE_UNAVAILABLE.value(), "登录服务暂不可用，请稍后重试");
        }
    }

    /**
     * 获取当前登录租户用户信息
     */
    @Operation(summary = "获取当前登录租户用户信息")
    @GetMapping("/getInfo")
    public ResultData<PlatformUserInfoResponse> getInfo() {
        CallerContext callerContext = CallerContextHolder.get();
        if (callerContext == null || callerContext.getTenantId() == null) {
            return ResultData.fail(401, "未登录或登录已失效");
        }
        Long tenantId = Long.parseLong(callerContext.getTenantId());
        Long userId = callerContext.getUserId();

        Tenant tenant = tenantService.selectTenantById(tenantId);
        PlatformUser user = platformUserService.selectUserById(userId);

        String[] permissions = callerContext.isSuperAdmin()
                ? new String[]{"*"} : new String[]{"platform:console"};
        return ResultData.ok(new PlatformUserInfoResponse(
                PlatformUserResponse.from(user), tenant, permissions));
    }

    /**
     * 中台退出登录
     */
    @Operation(summary = "中台退出登录")
    @PostMapping("/logout")
    public ResultData logout() {
        CallerContextHolder.clear();
        return ok("退出成功");
    }
}
