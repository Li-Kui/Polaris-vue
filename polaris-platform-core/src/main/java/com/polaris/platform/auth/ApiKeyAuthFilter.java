package com.polaris.platform.auth;

import com.alibaba.fastjson2.JSON;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.core.domain.AjaxResult;
import com.polaris.platform.domain.PlatformApiKey;
import com.polaris.platform.mapper.PlatformApiKeyMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * API Key 认证过滤器。
 * 拦截 /platform/api/** 请求，从 X-API-Key 头中校验 Key。
 */
@Component
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Autowired
    private PlatformApiKeyMapper apiKeyMapper;

    @Autowired
    private ApiKeyRateLimiter apiKeyRateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String keyValue = request.getHeader(API_KEY_HEADER);
        if (keyValue == null || keyValue.isBlank()) {
            sendError(response, 401, "缺少 X-API-Key 请求头");
            return;
        }

        PlatformApiKey apiKey = apiKeyMapper.selectAuthApiKeyByHash(ApiKeyDigestUtils.digest(keyValue));
        if (apiKey == null) {
            sendError(response, 401, "无效的 API Key");
            return;
        }

        if ("1".equals(apiKey.getStatus())) {
            sendError(response, 403, "API Key 已被停用");
            return;
        }

        if (apiKey.getExpireTime() != null && apiKey.getExpireTime().before(new Date())) {
            sendError(response, 403, "API Key 已过期");
            return;
        }

        ApiKeyRateLimiter.RateLimitResult rateLimitResult;
        try {
            rateLimitResult = apiKeyRateLimiter.tryAcquire(apiKey.getId(), apiKey.getRateLimit());
        } catch (Exception e) {
            log.error("API Key 限流检查失败, keyId={}", apiKey.getId(), e);
            sendError(response, HttpStatus.SERVICE_UNAVAILABLE.value(), "限流服务暂不可用，请稍后重试");
            return;
        }
        setRateLimitHeaders(response, rateLimitResult);
        if (!rateLimitResult.allowed()) {
            response.setHeader("Retry-After", String.valueOf(rateLimitResult.retryAfterSeconds()));
            sendError(response, HttpStatus.TOO_MANY_REQUESTS.value(), "API Key 调用过于频繁，请稍后重试");
            return;
        }

        // 更新最后使用时间（异步更佳，此处简化）
        apiKeyMapper.updateLastUsedTime(apiKey.getId());

        ApiKeyCallerContext context =
                new ApiKeyCallerContext(apiKey.getTenantId(), apiKey.getId(),
                        apiKey.getKeyName(), apiKey.getPermissions());
        CallerContextHolder.set(context);

        // 注入 Spring Security 上下文，由安全链统一校验开放API访问权限
        List<SimpleGrantedAuthority> authorities =
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_API"));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(context, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            CallerContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/platform/api/");
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.toJSONString(AjaxResult.error(status, message)));
    }

    private void setRateLimitHeaders(HttpServletResponse response,
                                     ApiKeyRateLimiter.RateLimitResult result) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset",
                String.valueOf(Instant.now().getEpochSecond() + result.retryAfterSeconds()));
    }
}
